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

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Set;

@UtilityClass
public class SafeMvelConstants {

    static final List<String> DEFAULT_IMPORT_PACKAGES = List.of("java.util.");

    static final Set<String> SAFE_JAVA_LANG_CLASSES = Set.of(
            "java.lang.Object", "java.lang.String", "java.lang.CharSequence",
            "java.lang.Comparable", "java.lang.Number",
            "java.lang.Integer", "java.lang.Long", "java.lang.Double",
            "java.lang.Float", "java.lang.Short", "java.lang.Byte",
            "java.lang.Character", "java.lang.Boolean", "java.lang.Void",
            "java.lang.Math", "java.lang.StrictMath", "java.lang.StringBuilder",
            "java.lang.Enum", "java.lang.Exception", "java.lang.Iterable"
    );

    static final Set<String> UNSAFE_CLASSES = Set.of(
            "Runtime", "ProcessBuilder", "Process",
            "System", "Class", "ClassLoader",
            "Thread", "SecurityManager", "Compiler"
    );

    static final String[] UNSAFE_METHODS = new String[] {
            "getClass", "forName",
            "getMethod", "getDeclaredMethod", "getMethods", "getDeclaredMethods",
            "getConstructor", "getDeclaredConstructor", "getConstructors", "getDeclaredConstructors",
            "getField", "getDeclaredField", "getFields", "getDeclaredFields",
            "invoke", "newInstance", "getRuntime",
            "exec", "start", "exit", "halt", "load", "loadLibrary"
    };

}
