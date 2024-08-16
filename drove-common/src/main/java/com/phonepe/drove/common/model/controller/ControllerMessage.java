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

package com.phonepe.drove.common.model.controller;

import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageHeader;

/**
 *
 */
public abstract class ControllerMessage extends Message<ControllerMessageType> {
    protected ControllerMessage(ControllerMessageType type, MessageHeader header) {
        super(type, header);
    }

    public abstract <T> T accept(final ControllerMessageVisitor<T> visitor);

}
