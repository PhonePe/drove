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

package com.phonepe.drove.models.operation;

import com.phonepe.drove.models.operation.localserviceops.*;

/**
 * To handle local service operation subtypes
 */
public interface LocalServiceOperationVisitor<T> {
    T visit(LocalServiceCreateOperation localServiceCreateOperation);

    T visit(LocalServiceAdjustInstancesOperation localServiceAdjustInstancesOperation);

    T visit(LocalServiceDeactivateOperation localServiceDeactivateOperation);

    T visit(LocalServiceRestartOperation localServiceRestartOperation);

    T visit(LocalServiceUpdateInstanceCountOperation localServiceUpdateInstanceCountOperation);

    T visit(LocalServiceDestroyOperation localServiceDestroyOperation);

    T visit(LocalServiceActivateOperation localServiceActivateOperation);

    T visit(LocalServiceReplaceInstancesOperation localServiceReplaceInstancesOperation);

    T visit(LocalServiceStopInstancesOperation localServiceStopInstancesOperation);
}
