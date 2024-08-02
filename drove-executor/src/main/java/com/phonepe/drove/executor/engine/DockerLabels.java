/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.engine;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DockerLabels {
    public static final String DROVE_JOB_TYPE_LABEL = "drove.job.type";
    public static final String DROVE_INSTANCE_ID_LABEL = "drove.instance.id";
    public static final String DROVE_INSTANCE_SPEC_LABEL = "drove.instance.spec";
    public static final String DROVE_INSTANCE_DATA_LABEL = "drove.instance.info";
}
