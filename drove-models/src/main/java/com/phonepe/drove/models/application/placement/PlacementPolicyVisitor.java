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

package com.phonepe.drove.models.application.placement;

import com.phonepe.drove.models.application.placement.policies.*;

/**
 *
 */
public interface PlacementPolicyVisitor<T> {
    T visit(OnePerHostPlacementPolicy onePerHost);

    T visit(MaxNPerHostPlacementPolicy maxNPerHost);

    T visit(MatchTagPlacementPolicy matchTag);

    T visit(NoTagPlacementPolicy noTag);

    T visit(RuleBasedPlacementPolicy ruleBased);

    T visit(AnyPlacementPolicy anyPlacementPolicy);

    T visit(CompositePlacementPolicy compositePlacementPolicy);

}
