/*
 * Copyright 2021. Santanu Sinha
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */

package com.phonepe.drove.controller.ui;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.cache.GuavaTemplateCache;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.io.TemplateSource;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.dropwizard.views.View;
import io.dropwizard.views.ViewRenderer;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * A {@link ViewRenderer} which renders Handlebars ({@code .hbs}) templates.
 */
@Slf4j
public class HandlebarsViewRenderer implements ViewRenderer {
    /**
     * For use by Handlebars.java internally.
     */
    private static final Cache<TemplateSource, Template> templateCache = CacheBuilder
            .newBuilder()
            .build();

    /**
     * Handlebars.java does not cache reads of Template content from resources.
     */
    @VisibleForTesting
    static final LoadingCache<String, Template> compilationCache = CacheBuilder
            .newBuilder()
            .build(new CacheLoader<String, Template>() {
                @Override
                public Template load(String srcUrl) throws Exception {
                    return HANDLEBARS.compile(srcUrl.replaceAll(".hbs$", ""));
                }
            });

    /**
     * Exposed for use in {@link HandlebarsHelperBundle} for miscellaneous configuration.
     */
    static final Handlebars HANDLEBARS = new Handlebars()
            .with(new GuavaTemplateCache(templateCache))
            .registerHelper("eq", ConditionalHelpers.eq)
            .registerHelper("neq", ConditionalHelpers.neq)
            .registerHelper("not", ConditionalHelpers.not)
            .registerHelper("or", ConditionalHelpers.or)
            .registerHelper("and", ConditionalHelpers.and)
            .registerHelper("gt", ConditionalHelpers.gt)
            .registerHelper("gte", ConditionalHelpers.gte)
            .registerHelper("lt", ConditionalHelpers.lt)
            .registerHelper("lte", ConditionalHelpers.lte)
            .registerHelpers(new CustomHelpers())
            ;

    public HandlebarsViewRenderer() {
        log.info("Handlebars view renderer created");
    }

    @Override
    public boolean isRenderable(View view) {
        return view.getTemplateName().endsWith(".hbs");
    }

    @Override
    public void render(View view, Locale locale, OutputStream output) throws IOException {
        try (Writer writer = new OutputStreamWriter(output, view.getCharset().orElse(StandardCharsets.UTF_8))) {
            compilationCache.get(view.getTemplateName()).apply(view, writer);
        } catch (FileNotFoundException | ExecutionException e) {
            throw new FileNotFoundException("Template " + view.getTemplateName() + " not found.");
        }
    }

    @Override
    public void configure(Map<String, String> options) {
        //do nothing
    }

    @Override
    public String getConfigurationKey() {
        return "handlebars";
    }
}
