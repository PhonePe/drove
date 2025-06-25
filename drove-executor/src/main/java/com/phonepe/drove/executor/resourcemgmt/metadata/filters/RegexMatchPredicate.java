/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.resourcemgmt.metadata.filters;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class RegexMatchPredicate implements Predicate<Map.Entry<String, String>> {

    private final List<Pattern> regexPatterns;

    public RegexMatchPredicate(final List<String> keysToFilter) {
        regexPatterns = keysToFilter.stream()
                .map(Pattern::compile)
                .toList();
    }

    @Override
    public boolean test(final Map.Entry<String, String> entry) {
        if ( Objects.isNull(entry) )
            return false;

        return regexPatterns.parallelStream()
                .anyMatch(pattern -> pattern.matcher(entry.getKey()).matches());
    }
}
