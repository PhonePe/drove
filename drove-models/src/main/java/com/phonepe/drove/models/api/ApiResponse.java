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

package com.phonepe.drove.models.api;

import lombok.Value;

/**
 *
 */
@Value
public class ApiResponse<T> {
    ApiErrorCode status;
    T data;
    String message;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ApiErrorCode.SUCCESS, data, "success");
    }

    public static <T> ApiResponse<T> failure(final String message) {
        return failure(null, message);
    }

    public static <T> ApiResponse<T> failure(final T data, final String message) {
        return new ApiResponse<>(ApiErrorCode.FAILED, data, message);
    }
}
