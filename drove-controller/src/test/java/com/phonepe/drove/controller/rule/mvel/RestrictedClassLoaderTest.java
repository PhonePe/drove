/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  You may not use this file except in compliance with the License.
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

package com.phonepe.drove.controller.rule.mvel;

import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RestrictedClassLoaderTest {

    @ParameterizedTest(name = "nameOnlyMatch(\"{1}\") == {0}")
    @MethodSource("nameOnlyMatchCases")
    void nameOnlyMatchPredicate(boolean expected, String className) {
        val predicate = RestrictedClassLoader.nameOnlyMatchPredicate();
        assertEquals(expected, predicate.test(className),
                     "Unexpected result for nameOnlyMatchPredicate with input: " + className);
    }

    static Stream<Arguments> nameOnlyMatchCases() {
        return Stream.of(

                Arguments.of(true, "[B"),                                     // --- array descriptors (start with '[') must be allowed ---
                Arguments.of(true, "[Ljava.lang.String;"),                    // String[]
                Arguments.of(true, "[[I"),                                    // int[][]
                Arguments.of(true, "org.mvel2.MVEL"),
                Arguments.of(true, "org.mvel2.compiler.CompiledExpression"),
                Arguments.of(true, "org.mvel2.ast.ASTNode"),
                Arguments.of(false, "java.lang.Runtime"),
                Arguments.of(false, "java.lang.ProcessBuilder"),
                Arguments.of(false, "java.lang.Thread"),
                Arguments.of(false, "java.lang.System"),
                Arguments.of(false, "java.util.ArrayList"),
                Arguments.of(false, "com.example.Foo"),
                Arguments.of(false, ""),
                Arguments.of(false, "org.mvel")
        );
    }

    @ParameterizedTest(name = "nameAndPrefixMatch(\"{1}\") == {0}")
    @MethodSource("nameAndPrefixMatchCases")
    void nameAndPrefixMatchPredicate(boolean expected, String className) {

        Set<String> allowedPrefixes = Set.of(
                "java.util.",
                "java.lang.String",
                "com.phonepe.drove."
        );

        val predicate = RestrictedClassLoader.nameAndPrefixMatchPredicate(allowedPrefixes);
        assertEquals(expected, predicate.test(className),
                     "Unexpected result for nameAndPrefixMatchPredicate with input: " + className);
    }

    static Stream<Arguments> nameAndPrefixMatchCases() {
        return Stream.of(
                Arguments.of(true, "java.util.ArrayList"),
                Arguments.of(true, "java.util.HashMap"),
                Arguments.of(true, "java.util.stream.Collectors"),
                Arguments.of(true, "com.phonepe.drove.models.Foo"),
                Arguments.of(true, "com.phonepe.drove.controller.SomeClass"),
                Arguments.of(true, "java.lang.String"),
                Arguments.of(false, "java.lang.StringBuffer"),               // not exact, 'java.lang.String' has no '.'
                Arguments.of(false, "java.lang.Runtime"),
                Arguments.of(false, "java.lang.ProcessBuilder"),
                Arguments.of(false, "java.io.File"),
                Arguments.of(false, "com.example.Other"),
                Arguments.of(false, ""),
                Arguments.of(false, "java.util"),                             // missing trailing '.'
                Arguments.of(false, "com.phonepe.droveX.Foo")                // prefix mismatch
        );
    }

    @ParameterizedTest(name = "emptyAllowlistRejectsAll(\"{0}\")")
    @MethodSource("anyClassNames")
    void emptyAllowlistRejectsAll(String className) {
        val predicate = RestrictedClassLoader.nameAndPrefixMatchPredicate(Set.of());
        assertFalse(predicate.test(className),
                    "Empty allowlist should reject every class name, but accepted: " + className);
    }

    static Stream<Arguments> anyClassNames() {
        return Stream.of(
                Arguments.of("java.lang.String"),
                Arguments.of("java.util.ArrayList"),
                Arguments.of("com.phonepe.drove.Foo"),
                Arguments.of("")
        );
    }
}

