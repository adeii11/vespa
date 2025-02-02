// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;

import java.time.Instant;
import java.util.Optional;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScalingEventData {

    @JsonProperty("from")
    public ClusterResourcesData from;

    @JsonProperty("to")
    public ClusterResourcesData to;

    @JsonProperty("at")
    public Long at;

    @JsonProperty("completion")
    public Long completion;

    public Cluster.ScalingEvent toScalingEvent() {
        return new Cluster.ScalingEvent(from.toClusterResources(), to.toClusterResources(), Instant.ofEpochMilli(at),
                                        toOptionalInstant(completion));
    }

    private Optional<Instant> toOptionalInstant(Long epochMillis) {
        if (epochMillis == null) return Optional.empty();
        return Optional.of(Instant.ofEpochMilli(epochMillis));
    }

}
