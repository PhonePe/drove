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
import org.mvel2.MVEL;
import org.mvel2.ParserConfiguration;
import org.mvel2.ParserContext;
import org.mvel2.compiler.AbstractParser;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import static com.phonepe.drove.controller.rule.mvel.SafeMvelConstants.UNSAFE_CLASSES;
import static com.phonepe.drove.controller.rule.mvel.SafeMvelConstants.UNSAFE_METHODS;
import static com.phonepe.drove.controller.rule.mvel.SafeMvelConstants.SAFE_JAVA_LANG_CLASSES;
import static com.phonepe.drove.controller.rule.mvel.SafeMvelConstants.DEFAULT_IMPORT_PACKAGES;

/* Sandboxed MVEL proxy
 */
@Singleton
public class SafeMvel {

    private static final Pattern STRING_LITERAL = Pattern.compile("(['\"]).*?\\1");

    private static final Pattern UNSAFE_CLASS_USAGE = Pattern.compile(
            "\\b(" + String.join("|", UNSAFE_CLASSES) + ")\\b");

    private static final Pattern BLOCKED_METHOD_CALL = Pattern.compile(
            "\\.(" + String.join("|",UNSAFE_METHODS) + ")\\(");

    private static final Pattern DOT_CLASS_ACCESS = Pattern.compile("\\.class(\\.|$)");

    static {
        UNSAFE_CLASSES.forEach(AbstractParser.CLASS_LITERALS::remove);
    }

    private final ParserConfiguration parserConfiguration;

    @Inject
    public SafeMvel(@Named("MvelRuleAllowedImportPackages") final List<String> importPackages) {
        this.parserConfiguration = createParserConfiguration(importPackages);
    }

    public Serializable compile(String expression) {
        rejectDangerousExpressions(expression);
        return MVEL.compileExpression(expression, new ParserContext(parserConfiguration));
    }

    public Object execute(Serializable compiledExpression, Object data) {
        return MVEL.executeExpression(compiledExpression, data, new MapVariableResolverFactory(new HashMap<>()));
    }

    private static void rejectDangerousExpressions(String rule) {
        val stripped = STRING_LITERAL.matcher(rule).replaceAll("");

        if (UNSAFE_CLASS_USAGE.matcher(stripped).find()) {
            throw new SecurityException("Rule contains blocked class reference");
        }
        if (BLOCKED_METHOD_CALL.matcher(stripped).find()) {
            throw new SecurityException("Rule contains blocked method call");
        }
        if (DOT_CLASS_ACCESS.matcher(stripped).find()) {
            throw new SecurityException("Rule contains blocked .class access");
        }
    }

    private ParserConfiguration createParserConfiguration(List<String> importPackages) {
        val parserConfig = new ParserConfiguration();

        val allowedPrefixes = new HashSet<>(SAFE_JAVA_LANG_CLASSES);
        allowedPrefixes.addAll(DEFAULT_IMPORT_PACKAGES);
        importPackages.stream()
                .map(pkg -> pkg.endsWith(".") ? pkg : pkg + ".")
                .forEach(allowedPrefixes::add);

        parserConfig.setClassLoader(new RestrictedClassLoader(getClass().getClassLoader(), allowedPrefixes));
        DEFAULT_IMPORT_PACKAGES.forEach(parserConfig::addPackageImport);
        importPackages.forEach(parserConfig::addPackageImport);
        return parserConfig;
    }
}
