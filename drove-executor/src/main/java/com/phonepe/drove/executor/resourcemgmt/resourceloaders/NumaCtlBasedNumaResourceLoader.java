/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.phonepe.drove.executor.utils.ExecutorUtils.mapCores;

/**
 * Runs 'numactl -H' to populate resource topology of the machine. This is used by controller to allocate containers
 */
@Slf4j
@Singleton
public class NumaCtlBasedNumaResourceLoader implements NumaResourceLoader {
    private final ResourceConfig resourceConfig;

    @Inject
    public NumaCtlBasedNumaResourceLoader(ResourceConfig resourceConfig) {
        this.resourceConfig = resourceConfig;
    }

    @Override
    @SneakyThrows
    public Map<Integer, ResourceManager.NodeInfo> loadSystemResources() {
        if (CommonUtils.isRunningOnMacOS()) {
            log.info("Since numactl is not supported on systems running Mac OS, " +
                    "using sysctl to automatically determine node resources");
            return fetchSystemResourcesUsingSysCTL();
        }
        return parseNumaCTLOutput(fetchSystemResourceUsingNumaCTL());
    }

    protected List<String> fetchSystemResourceUsingNumaCTL() throws Exception {
        val numaCtlExec = Objects.requireNonNullElse(System.getenv("NUMA_CTL_PATH"), "/usr/bin/numactl");
        log.debug("numactl executable path: {}", numaCtlExec);
        val process = new ProcessBuilder(numaCtlExec, "-H").start();
        var lines = Collections.<String>emptyList();
        try (val input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            lines = input.lines().toList();
        }
        val exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("'numactl -H' returned: " + exitCode);
        }
        return lines;
    }

    /**
     * This utility method runs commands to determine available vCores and Memory on Mac OS since `numactl` is unavailable.
     * Note: This method should only be used on Mac OS since the commands used are OS specific.
     * @return a {@code Map} between the numa node id to {@code NodeInfo}
     * @throws Exception - when command execution fails
     */
    protected Map<Integer, ResourceManager.NodeInfo> fetchSystemResourcesUsingSysCTL() throws Exception {
        Preconditions.checkState(CommonUtils.isRunningOnMacOS(),
                "Sysctl should be used only on Mac OS for determining available vCores and Memory");
        String coreCountCmd = "/usr/sbin/sysctl -a | grep machdep.cpu.core.count | awk '{print $2}'";
        String memorySizeCmd = "/usr/sbin/sysctl -a | grep 'hw.memsize:' | awk '{print $2}'";
        int physicalCoreCount = CommandUtils.parseCommandOutput(coreCountCmd, true)
                .stream()
                .findFirst()
                .map(Integer::parseInt)
                .orElseThrow(() -> new RuntimeException("Unexpected empty command output [command = %s]".formatted(coreCountCmd)));
        long memorySizeInBytes = CommandUtils.parseCommandOutput(memorySizeCmd, true)
                .stream()
                .findFirst()
                .map(Long::parseLong)
                .orElseThrow(() -> new RuntimeException("Unexpected empty command output [command = %s]".formatted(memorySizeCmd)));
        Set<Integer> physicalCores = new HashSet<>();
        for (int i = 0; i < physicalCoreCount; i++) {
            physicalCores.add(i);
        }
        // convert memory returned by sysctl from bytes to MiB
        long memoryInMiB = memorySizeInBytes / (1024 * 1024);
        long exposedMemoryInMiB = (long) (memoryInMiB * (resourceConfig.getExposedMemPercentage() / 100.0));
        return Map.of(
                0, ResourceManager.NodeInfo
                        .from(physicalCores, exposedMemoryInMiB));
    }

    /**
     * This utility method parses the output to identify the mapping between vCPU cores, memory size and NUMA nodes.
     * The output for `numactl -H` commands looks like below on Linux systems.
     * Output:
     * available: 2 nodes (0-1)
     * node 0 cpus: 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59
     * node 0 size: 241567 MB
     * node 0 free: 207169 MB
     * node 1 cpus: 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79
     * node 1 size: 290264 MB
     * node 1 free: 248480 MB
     * node distances:
     * node   0   1
     *   0:  10  21
     *   1:  21  10
     */
    protected Map<Integer, ResourceManager.NodeInfo> parseNumaCTLOutput(final List<String> lines) {
        val cores = fetchNodeToCPUMap(lines);
        val mem = fetchNodeToMemoryMap(lines);
        if (!Sets.difference(cores.keySet(), mem.keySet()).isEmpty()) {
            throw new IllegalStateException("Mismatch between memory nodes and cores");
        }
        return cores.entrySet().stream()
                .map(e -> new Pair<>(
                        e.getKey(),
                        new ResourceManager.NodeInfo(
                                e.getValue(),
                                mapCores(e.getValue()),
                                mem.get(e.getKey()),
                                e.getValue(),
                                mem.get(e.getKey()))))
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
    }

    private Map<Integer, Set<Integer>> fetchNodeToCPUMap(List<String> lines) {
        return lines.stream().filter(line -> line.matches("node \\d+ cpus:.*")).map(line -> {
            val node = nodeNum(line);
            if (-1 == node) {
                throw new IllegalStateException("Did not find any node");
            }
            val cpus = cores(line);
            return new Pair<>(node, cpus);
        }).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
    }

    private Map<Integer, Long> fetchNodeToMemoryMap(List<String> lines) {
        val nodePattern = Pattern.compile("\\d+");
        val exposedMemPercentage = (resourceConfig.getExposedMemPercentage()) / 100.0;
        return lines.stream().filter(line -> line.matches("node \\d+ size: .*")).map(line -> {
            val node = nodeNum(line);
            if (-1 == node) {
                throw new IllegalStateException("Did not find any node");
            }
            val m1 = nodePattern.matcher(line.substring(line.indexOf(":")));
            if (!m1.find()) {
                throw new IllegalStateException();
            }
            return new Pair<>(node, (long) (Long.parseLong(m1.group()) * exposedMemPercentage));
        }).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
    }

    private int nodeNum(final String cpuLine) {
        return new Scanner(new StringReader(cpuLine.substring(0,
                                                              cpuLine.indexOf(':')))).findAll(Pattern.compile("\\d+"))
                .findAny()
                .map(m -> Integer.parseInt(m.group()))
                .filter(i -> i >= 0)
                .orElse(-1);
    }

    private Set<Integer> cores(final String line) {
        return new Scanner(new StringReader(line.substring(line.indexOf(':')))).findAll(Pattern.compile("\\d+"))
                .map(r -> Integer.parseInt(r.group()))
                .filter(i -> i >= 0)
                .filter(core -> !Objects.requireNonNullElse(resourceConfig.getOsCores(), Set.<Integer>of()).contains(core))
                .collect(Collectors.toUnmodifiableSet());
    }
}
