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

package com.phonepe.drove.controller.zookeeper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Controller-specific ZooKeeper and utility methods.
 * Contains methods for:
 * - Building and managing Curator clients for ZooKeeper operations
 * - Jackson ObjectMapper configuration
 * - Common list operations
 * 
 * This class consolidates controller-level utilities to remove ZooKeeper 
 * dependency from the common module while providing reusable helpers for tests.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ControllerZkUtils {
    private static final String DEFAULT_NAMESPACE = "drove";

    /**
     * Build a CuratorFramework instance for ZooKeeper operations.
     * This method was previously in CommonUtils but is now controller-specific
     * to remove ZooKeeper dependency from the common module.
     *
     * @param config ZkConfig with connection details
     * @return Configured CuratorFramework instance
     */
    public static CuratorFramework buildCurator(ZkConfig config) {
        return CuratorFrameworkFactory.builder()
                .connectString(config.getConnectionString())
                .namespace(Objects.requireNonNullElse(config.getNameSpace(), DEFAULT_NAMESPACE))
                .retryPolicy(new RetryForever(1000))
                .sessionTimeoutMs(30_000)
                .build();
    }

    /**
     * Configure a Jackson ObjectMapper with standard Drove settings.
     * Registers modules for parameter names, Java time, and configures serialization.
     *
     * @param objectMapper The ObjectMapper to configure
     */
    @IgnoreInJacocoGeneratedReport
    @SuppressWarnings("deprecation")
    public static void configureMapper(ObjectMapper objectMapper) {
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    /**
     * Extract a sublist from a list with bounds checking.
     * Returns an empty list if the input is null, empty, or if start index is out of bounds.
     *
     * @param list The source list
     * @param start The start index (inclusive)
     * @param size The maximum size of the sublist to extract
     * @param <T> The type of list elements
     * @return A sublist or empty list if conditions not met
     */
    @IgnoreInJacocoGeneratedReport
    public static <T> List<T> sublist(final List<T> list, int start, int size) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        final int listSize = list.size();
        if (listSize < start + 1) {
            return Collections.emptyList();
        }
        final int end = Math.min(listSize, start + size);
        return list.subList(start, end);
    }
}
