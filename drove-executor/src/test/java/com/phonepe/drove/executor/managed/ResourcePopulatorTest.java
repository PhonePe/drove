package com.phonepe.drove.executor.managed;

import com.google.common.base.Strings;
import com.phonepe.drove.common.model.utils.Pair;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 */
class ResourcePopulatorTest {

    @Test
    void test() {
        val out = List.of("available: 2 nodes (0-1)NEWLINE",
                          "node 0 cpus: 0 2 4 6 8 10 12 14 16 18 20 22 24 26 28 30 32 34 36 38\n",
                          "node 0 size: 192188 MB\n",
                          "node 0 free: 189684 MB\n",
                          "node 1 cpus: 1 3 5 7 9 11 13 15 17 19 21 23 25 27 29 31 33 35 37 39\n",
                          "node 1 size: 193530 MB\n",
                          "node 1 free: 191791 MB\n",
                          "node distances:\n",
                          "node   0   1 \n",
                          "  0:  10  21 \n",
                          "  1:  21  10 \n");
        read(out);
    }

    void read(List<String> lines) {

        val nodePattern = Pattern.compile("\\d+");
        val numberPattern = Pattern.compile("\\d+");
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
        System.out.println(cores);
        System.out.println(mem);
    }

    @Test
    void test1() {
        val line = "node 0 cpus: 0 2 4 6 8 10 12 14 16 18 20 22 24 26 28 30 32 34 36 38";
        val nodePattern = Pattern.compile("\\d+");
/*        val m = nodePattern.matcher(line.substring(0, line.indexOf(':')));
        val node = Integer.parseInt(m.group());
        System.out.println("node: " + node);*/
        {
            val s = new Scanner(new StringReader(line.substring(0, line.indexOf(':'))));
            System.out.println("Node: " + s.findAll(Pattern.compile("\\p{Digit}+"))
                    .findAny()
                    .map(MatchResult::group)
                    .orElse("NAN"));
        }
        {
            val s = new Scanner(new StringReader(line.substring(line.indexOf(':'))));
            s.findAll(Pattern.compile("\\p{Digit}+")).forEach(r -> System.out.println(r.group()));
        }
/*        val m1 = nodePattern.matcher(line.substring(line.indexOf(":")));
        val p = new Pair<>(node, Long.parseLong(m1.group()));
        System.out.println(p);*/
    }


}