package com.phonepe.drove.executor.resourcemgmt;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.model.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 *
 */
@Slf4j
public class NumaCtlBasedResourceLoader {
    private final ResourceConfig resourceConfig;

    @Inject
    public NumaCtlBasedResourceLoader(ResourceConfig resourceConfig) {
        this.resourceConfig = resourceConfig;
    }


    public Map<Integer, ResourceManager.NodeInfo> parseCommandOutput(List<String> lines) {
        val nodePattern = Pattern.compile("\\d+");
        val cores = lines.stream()
                .filter(line -> line.matches("node \\d+ cpus:.*"))
                .map(line -> {
                    val node = nodeNum(line);
                    if (-1 == node) {
                        throw new IllegalStateException("Did not find any node");
                    }
                    val cpus = cores(line);
                    return new Pair<>(node, cpus);
                })
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
        val exposedMemPercentage = (resourceConfig.getExposedMemPercentage()) / 100.0;
        val mem = lines.stream()
                .filter(line -> line.matches("node \\d+ size: .*"))
                .map(line -> {
                    val node = nodeNum(line);
                    if (-1 == node) {
                        throw new IllegalStateException("Did not find any node");
                    }
                    val m1 = nodePattern.matcher(line.substring(line.indexOf(":")));
                    if (!m1.find()) {
                        throw new IllegalStateException();
                    }
                    return new Pair<>(node, (long) (Long.parseLong(m1.group()) * exposedMemPercentage));
                })
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
        if (!Sets.difference(cores.keySet(), mem.keySet()).isEmpty()) {
            throw new IllegalStateException("Mismatch between memory nodes and cores");
        }
        log.info("NUMA Optimisation: {}",
                resourceConfig.isDisableNUMAPinning()
                        ? "Off"
                        : "On");
        val resources = fetchResourceMap(cores, mem);
        if (!resources.isEmpty()) {
            log.info("Found resources:");
            resources.forEach((id, info) -> log.info(
                    "    Node " + id
                            + ": Cores: " + info.getAvailableCores()
                            .stream()
                            .sorted()
                            .toList()
                            + " Memory (MB): " + info.getMemoryInMB()));

        } else {
            log.error("No usable resources found");
        }
        return resources;
    }

    private Map<Integer, ResourceManager.NodeInfo> fetchResourceMap(
            Map<Integer, Set<Integer>> cores,
            Map<Integer, Long> mem
    ) {
        if (resourceConfig.isDisableNUMAPinning()) {
            var coreStream = cores.values()
                    .stream()
                    .flatMap(Collection::stream);

            var memory = mem.values()
                    .stream()
                    .mapToLong(i -> i)
                    .sum();
            if (resourceConfig.getBurstUpConfiguration().isBurstUpEnabled()) {
                coreStream = IntStream.rangeClosed(
                                0,
                        ((int) coreStream.count() *
                                        resourceConfig.getBurstUpConfiguration().getCpuBurstUpMultiplier()) - 1
                        )
                        .boxed();
                memory = memory * resourceConfig.getBurstUpConfiguration().getMemoryBurstUpMultiplier();
            }
                    return Map.of(0,
                            new ResourceManager.NodeInfo(
                                    coreStream
                                            .collect(Collectors.toUnmodifiableSet()),
                                    memory
                                    ));
        }
        return cores.entrySet()
                .stream()
                .map(e -> new Pair<>(e.getKey(),
                        new ResourceManager.NodeInfo(e.getValue(), mem.get(e.getKey()))))
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
    }

    private int nodeNum(final String cpuLine) {
        return new Scanner(new StringReader(cpuLine.substring(0, cpuLine.indexOf(':'))))
                .findAll(Pattern.compile("\\d+"))
                .findAny()
                .map(m -> Integer.parseInt(m.group()))
                .filter(i -> i >= 0)
                .orElse(-1);
    }

    private Set<Integer> cores(final String line) {
        return new Scanner(new StringReader(line.substring(line.indexOf(':'))))
                .findAll(Pattern.compile("\\d+"))
                .map(r -> Integer.parseInt(r.group()))
                .filter(i -> i >= 0)
                .filter(core -> !resourceConfig.getOsCores().contains(core))
                .collect(Collectors.toUnmodifiableSet());
    }
}
