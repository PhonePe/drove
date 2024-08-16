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

package com.phonepe.drove.controller.errorhandlers;

import com.phonepe.drove.controller.utils.ControllerUtils;
import io.dropwizard.jersey.validation.ConstraintMessage;
import io.dropwizard.jersey.validation.JerseyViolationException;
import lombok.val;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Handles param validations from jersey and returns standard error
 */
@Provider
public class ValidationErrorHandler implements ExceptionMapper<JerseyViolationException> {
    @Override
    public Response toResponse(JerseyViolationException exception) {
        val invocable = exception.getInvocable();
        return ControllerUtils.badRequest(Map.of("validationErrors", exception.getConstraintViolations()
                                                  .stream()
                                                  .map(violation -> ConstraintMessage.getMessage(violation, invocable))
                                                  .toList()), "Command validation failure");
    }
}
