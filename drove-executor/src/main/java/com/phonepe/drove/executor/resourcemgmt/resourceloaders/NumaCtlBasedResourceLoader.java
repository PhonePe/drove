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

import com.google.common.collect.Sets;
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
public class NumaCtlBasedResourceLoader implements ResourceLoader {
    private final ResourceConfig resourceConfig;

    @Inject
    public NumaCtlBasedResourceLoader(ResourceConfig resourceConfig) {
        this.resourceConfig = resourceConfig;
    }


    @Override
    @SneakyThrows
    public Map<Integer, ResourceManager.NodeInfo> loadSystemResources() {
        return parseCommandOutput(fetchSystemResourceUsingNumaCTL());
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

    private Map<Integer, ResourceManager.NodeInfo> parseCommandOutput(List<String> lines) {

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
