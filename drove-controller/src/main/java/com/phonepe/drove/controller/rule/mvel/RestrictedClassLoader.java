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

import com.google.common.annotations.VisibleForTesting;

import java.util.Set;
import java.util.function.Predicate;

class RestrictedClassLoader extends ClassLoader {

    private static final Predicate<String> nameCheck = nameOnlyMatchPredicate();
    private final Predicate<String> nameAndPrefixCheck;

    RestrictedClassLoader(ClassLoader parent, Set<String> allowedPackagePrefixes) {
        super(parent);
        this.nameAndPrefixCheck = nameAndPrefixMatchPredicate(allowedPackagePrefixes);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (nameCheck.test(name)) {
            return super.loadClass(name);
        }

        if (nameAndPrefixCheck.test(name)) {
            return super.loadClass(name);
        }

        throw new ClassNotFoundException("Blocked class: " + name);
    }

    @VisibleForTesting
    static Predicate<String> nameOnlyMatchPredicate() {
        return name -> (name.startsWith("[") || name.startsWith("org.mvel2."));
    }

    @VisibleForTesting
    static Predicate<String> nameAndPrefixMatchPredicate(Set<String> allowedPackagePrefixes) {
        return name -> (allowedPackagePrefixes.stream().anyMatch(prefix ->
                prefix.endsWith(".") ? name.startsWith(prefix) : name.equals(prefix)));
    }
}
