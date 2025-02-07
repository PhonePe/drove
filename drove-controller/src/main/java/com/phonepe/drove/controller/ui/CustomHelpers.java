/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.ui;

import com.google.common.base.Joiner;
import com.phonepe.drove.controller.config.ViewOptions;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocationVisitor;

/**
 *
 */
@SuppressWarnings("unused")
public class CustomHelpers {

    public CharSequence resourceRepr(final ResourceAllocation resource) {

        return resource.accept(new ResourceAllocationVisitor<>() {
            @Override
            public CharSequence visit(CPUAllocation cpu) {
                return Joiner.on("<br>")
                        .join(cpu.getCores()
                                      .entrySet()
                                      .stream()
                                      .map(entry -> String.format(
                                              "<b>NUMA Node: </b> <span class=\"badge bg-info\">%s</span> &nbsp; &nbsp;<b>Allocated Cores:</b> %s",
                                              entry.getKey(),
                                              Joiner.on("&nbsp;")
                                                      .join(entry.getValue()
                                                                    .stream()
                                                                    .map(value -> "<span class=\"badge bg-primary\">" + value + "</span>")
                                                                    .toList())))
                                      .toList());
            }

            @Override
            public CharSequence visit(MemoryAllocation memory) {
                return Joiner.on("<br")
                        .join(memory.getMemoryInMB()
                                      .entrySet()
                                      .stream()
                                      .map(e -> String.format(
                                              "<b>NUMA Node:</b> <span class=\"badge bg-info\">%s</span>  &nbsp; &nbsp;<b>Allocated Memory:</b> %d MB",
                                              e.getKey(),
                                              e.getValue()))
                                      .toList());
            }
        });
    }

    public CharSequence badgeColor(final ViewOptions.Criticality criticality) {
        return switch (criticality) {
            case LOCAL -> "secondary";
            case DEVELOPMENT -> "success";
            case INTEGRATION -> "warning";
            case PRODUCTION -> "danger";
        };
    }
}
