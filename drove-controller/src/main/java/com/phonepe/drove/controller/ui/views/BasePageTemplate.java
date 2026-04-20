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

package com.phonepe.drove.controller.ui.views;

import com.phonepe.drove.controller.config.InstallationMetadata;
import lombok.Getter;
import ru.vyarus.guicey.gsp.views.template.TemplateView;

import java.util.Objects;

/**
 * Base class for all view templates.
 * <p>
 * Provides a concrete equals/hashCode implementation based on {@code installationMetadata}
 * so that Lombok {@code @EqualsAndHashCode(callSuper = true)} in subclasses has a proper
 * parent implementation to delegate to (the {@link TemplateView} → {@code View} ancestry
 * does not override Object identity equality).
 * </p>
 */
@Getter
public abstract class BasePageTemplate extends TemplateView {
    private final InstallationMetadata installationMetadata;
    private final boolean footerSummary;

    protected BasePageTemplate(String templatePath, InstallationMetadata installationMetadata) {
        this(templatePath, installationMetadata, true);
    }

    protected BasePageTemplate(String templatePath, InstallationMetadata installationMetadata, boolean footerSummary) {
        super(templatePath);
        this.installationMetadata = installationMetadata;
        this.footerSummary = footerSummary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BasePageTemplate that = (BasePageTemplate) o;
        return Objects.equals(installationMetadata, that.installationMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(installationMetadata);
    }
}
