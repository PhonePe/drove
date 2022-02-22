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

import com.google.common.base.Joiner;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocationVisitor;

import java.util.Set;

/**
 *
 */

public class CustomHelpers {

    public CharSequence resourceRepr(final ResourceAllocation resource) {
        record NodeResource(Set<Integer> cores, long mem) {};
        return resource.accept(new ResourceAllocationVisitor<CharSequence>() {
            @Override
            public CharSequence visit(CPUAllocation cpu) {
                return Joiner.on("<br>")
                        .join(cpu.getCores()
                                      .entrySet()
                                      .stream()
                                      .map(entry -> String.format("<b>Node: </b> %s: <b>Cores:</b> %s",
                                                                  entry.getKey(),
                                                                  Joiner.on(", ").join(entry.getValue())))
                                      .toList());
            }

            @Override
            public CharSequence visit(MemoryAllocation memory) {
                return Joiner.on("<br")
                        .join(memory.getMemoryInMB()
                                      .entrySet()
                                      .stream()
                                      .map(e -> String.format("<b>Node:</b> %s <b>Memory:</b> %d MB", e.getKey(), e.getValue()))
                                      .toList());
            }
        });
    }
}
