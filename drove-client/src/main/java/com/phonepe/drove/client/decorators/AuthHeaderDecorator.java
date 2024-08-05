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

package com.phonepe.drove.client.decorators;

import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.RequestDecorator;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 *
 */
@Value
@Slf4j
public class AuthHeaderDecorator implements RequestDecorator {

    String authHeader;

    @Override
    public void decorateRequest(DroveClient.Request request) {
        if(!Strings.isNullOrEmpty(authHeader)) {
            request.headers().put(HttpHeaders.AUTHORIZATION, List.of(authHeader));
            log.trace("Added header auth to request.");
        }
        else {
            log.trace("Did not add header auth to request as no auth header config present.");
        }
    }
}
