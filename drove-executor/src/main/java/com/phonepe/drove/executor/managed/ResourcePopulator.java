package com.phonepe.drove.executor.managed;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.executor.resource.ResourceDB;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
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

    @Inject
    public ResourcePopulator(ResourceDB resourceDB) {
        this.resourceDB = resourceDB;
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
                    val m = nodePattern.matcher(line.substring(0, line.indexOf(':')));
                    if (!m.find()) {
                        throw new IllegalStateException("Did not find any node");
                    }
                    val node = Integer.parseInt(m.group());
                    val cpus = Arrays.stream(line.replaceAll("node \\d+ cpus: ", "").split("\\s*"))
                            .filter(s -> !Strings.isNullOrEmpty(s))
                            .map(Integer::parseInt)
                            .collect(Collectors.toUnmodifiableSet());
                    return new Pair<>(node, cpus);
                })
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
        val mem = lines.stream()
                .filter(line -> line.matches("node \\d+ size: .*"))
                .map(line -> {
                    val m = nodePattern.matcher(line.substring(0, line.indexOf(':')));
                    if (!m.find()) {
                        throw new IllegalStateException();
                    }
                    val node = Integer.parseInt(m.group());
                    val m1 = nodePattern.matcher(line.substring(line.indexOf(":")));
                    if (!m1.find()) {
                        throw new IllegalStateException();
                    }
                    return new Pair<>(node, Long.parseLong(m1.group()));
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
}
