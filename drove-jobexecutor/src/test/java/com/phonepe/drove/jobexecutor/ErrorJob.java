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

package com.phonepe.drove.jobexecutor;

/**
 *
 */
public class ErrorJob implements Job<Integer> {

    @Override
    public String jobId() {
        return "error-job";
    }

    @Override
    public void cancel() {

    }

    @Override
    public Integer execute(
            JobContext<Integer> context,
            JobResponseCombiner<Integer> responseCombiner) {
        throw new IllegalStateException("Error for testing");
    }
}
