package com.phonepe.drove.models.common;

import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
public class ClusterStateData {
    ClusterState state;
    Date updated;
}
