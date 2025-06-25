/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.phonepe.drove.executor.resourcemgmt.metadata.filters;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;


class RegexMatchPredicateTest {

    @Test
    void testExactMatch() {
        // Arrange
        List<String> keysToFilter = List.of("EXACT_KEY");
        Predicate<Map.Entry<String, String>> predicate = new RegexMatchPredicate(keysToFilter);

        // Act & Assert
        assertTrue(predicate.test(Map.entry("EXACT_KEY", "value")));
        assertFalse(predicate.test(Map.entry("EXACT_KEY_EXTRA", "value")));
    }

    @Test
    void testPrefixMatch() {
        // Arrange
        List<String> keysToFilter = List.of("PREFIX_.*");
        Predicate<Map.Entry<String, String>> predicate = new RegexMatchPredicate(keysToFilter);

        // Act & Assert
        assertTrue(predicate.test(Map.entry("PREFIX_123", "value")));
        assertTrue(predicate.test(Map.entry("PREFIX_", "value")));
        assertFalse(predicate.test(Map.entry("NOT_PREFIX_123", "value")));
    }

    @Test
    void testSuffixMatch() {
        // Arrange
        List<String> keysToFilter = List.of(".*_SUFFIX$");
        Predicate<Map.Entry<String, String>> predicate = new RegexMatchPredicate(keysToFilter);

        // Act & Assert
        assertTrue(predicate.test(Map.entry("123_SUFFIX", "value")));
        assertTrue(predicate.test(Map.entry("_SUFFIX", "value")));
        assertFalse(predicate.test(Map.entry("123_SUFFIX_EXTRA", "value")));
    }

    @Test
    void testPartialMatch() {
        // Arrange
        List<String> keysToFilter = List.of(".*PARTIAL.*");
        Predicate<Map.Entry<String, String>> predicate = new RegexMatchPredicate(keysToFilter);

        // Act & Assert
        assertTrue(predicate.test(Map.entry("123_PARTIAL_456", "value")));
        assertTrue(predicate.test(Map.entry("PARTIAL", "value")));
        assertFalse(predicate.test(Map.entry("123_PART", "value")));
    }

    @Test
    void testMultiplePatterns() {
        // Arrange
        List<String> keysToFilter = List.of("^EXACT_KEY$", "PREFIX_.*", ".*_SUFFIX$", ".*PARTIAL.*");
        Predicate<Map.Entry<String, String>> predicate = new RegexMatchPredicate(keysToFilter);

        // Act & Assert
        assertTrue(predicate.test(Map.entry("EXACT_KEY", "value")));
        assertTrue(predicate.test(Map.entry("PREFIX_123", "value")));
        assertTrue(predicate.test(Map.entry("123_SUFFIX", "value")));
        assertTrue(predicate.test(Map.entry("123_PARTIAL_456", "value")));
        assertFalse(predicate.test(Map.entry("UNMATCHED_KEY", "value")));
    }

    @Test
    void testNullEntry() {
        // Arrange
        List<String> keysToFilter = List.of(".*");
        Predicate<Map.Entry<String, String>> predicate = new RegexMatchPredicate(keysToFilter);

        // Act & Assert
        assertFalse(predicate.test(null));
    }

    @Test
    void testEmptyPatterns() {
        // Arrange
        List<String> keysToFilter = List.of();
        Predicate<Map.Entry<String, String>> predicate = new RegexMatchPredicate(keysToFilter);

        assertFalse(predicate.test(Map.entry("ANY_KEY", "value")));
    }


}
