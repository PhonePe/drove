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

import com.github.jknack.handlebars.Helper;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HandlebarsHelperBundle<C extends Configuration> implements ConfiguredBundle<C> {

    protected abstract void configureHandlebars(C configuration);

    @Override
    public final void initialize(Bootstrap<?> bootstrap) {
        log.info("Initialized handlebars view renderer");
    }

    @Override
    public final void run(C configuration, Environment environment) {
        configureHandlebars(configuration);
    }

    public static <H> void registerHelperMissing(Helper<H> helper) {
        HandlebarsViewRenderer.HANDLEBARS.registerHelperMissing(helper);
    }

    public static <H> void registerHelper(String name, Helper<H> helper) {
        HandlebarsViewRenderer.HANDLEBARS.registerHelper(name, helper);
    }

    public static void registerHelpers(Object helperSource) {
        HandlebarsViewRenderer.HANDLEBARS.registerHelpers(helperSource);
    }

    public static void setPrettyPrint(boolean prettyPrint) {
        HandlebarsViewRenderer.HANDLEBARS.setPrettyPrint(prettyPrint);
    }

    public static void setInfiniteLoops(boolean infiniteLoops) {
        HandlebarsViewRenderer.HANDLEBARS.setInfiniteLoops(infiniteLoops);
    }
}
