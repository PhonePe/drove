package com.phonepe.drove.executor.managed;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceDB;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Singleton
@Order(0)
public class ResourcePopulator implements Managed {
    private final ResourceDB resourceDB;
    private final ResourceConfig resourceConfig;

    @Inject
    public ResourcePopulator(ResourceDB resourceDB, ResourceConfig resourceConfig) {
        this.resourceDB = resourceDB;
        this.resourceConfig = resourceConfig;
    }

    @Override
    public void start() throws Exception {
        val process = new ProcessBuilder("numactl", "-H").start();
        var lines = Collections.<String>emptyList();
        try (val input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            lines = input.lines()
                    .collect(Collectors.toUnmodifiableList());
        }
        val exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("'numactl -H' returned: " + exitCode);
        }
        val nodePattern = Pattern.compile("\\d+");
        val cores = lines.stream()
                .filter(line -> line.matches("node \\d+ cpus:.*"))
                .map(line -> {
                    val node = nodeNum(line);
                    if(-1 == node) {
                        throw new IllegalStateException("Did not find any node");
                    }
                    val cpus = cores(line);
                    return new Pair<>(node, cpus);
                })
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
        val exposedMemPercentage = ((double)resourceConfig.getExposedMemPercentage()) / 100.0;
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
                    return new Pair<>(node, (long)(Long.parseLong(m1.group()) * exposedMemPercentage));
                })
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
        if(!Sets.difference(cores.keySet(), mem.keySet()).isEmpty()) {
            throw new IllegalStateException();
        }
        val resources = cores.entrySet()
                .stream()
                .map(e -> new Pair<>(e.getKey(), new ResourceDB.NodeInfo(e.getValue(), mem.get(e.getKey()))))
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
        log.info("Found resources: {}", resources);
        resourceDB.populateResources(resources);
    }

    @Override
    public void stop() throws Exception {

    }

    private int nodeNum(final String cpuLine) {
        return new Scanner(new StringReader(cpuLine.substring(0, cpuLine.indexOf(':'))))
                .findAll(Pattern.compile("\\p{Digit}+"))
                .findAny()
                .map(m -> Integer.parseInt(m.group()))
                .orElse(-1);
    }

    private Set<Integer> cores(final String line) {
        return new Scanner(new StringReader(line.substring(line.indexOf(':'))))
            .findAll(Pattern.compile("\\p{Digit}+"))
                .map(r -> Integer.parseInt(r.group()))
                .filter(core -> !resourceConfig.getOsCores().contains(core))
                .collect(Collectors.toUnmodifiableSet());
    }
}
