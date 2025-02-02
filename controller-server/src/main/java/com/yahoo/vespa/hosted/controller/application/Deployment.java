// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.TokenId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A deployment of an application in a particular zone.
 * 
 * @author bratseth
 * @author smorgrav
 */
public class Deployment {

    private final ZoneId zone;
    private final CloudAccount cloudAccount;
    private final RevisionId revision;
    private final Version version;
    private final Instant deployTime;
    private final DeploymentMetrics metrics;
    private final DeploymentActivity activity;
    private final QuotaUsage quota;
    private final OptionalDouble cost;
    private final Map<TokenId, Instant> dataPlaneTokens;

    public Deployment(ZoneId zone, CloudAccount cloudAccount, RevisionId revision, Version version, Instant deployTime,
                      DeploymentMetrics metrics, DeploymentActivity activity, QuotaUsage quota, OptionalDouble cost,
                      Map<TokenId, Instant> dataPlaneTokens) {
        this.zone = Objects.requireNonNull(zone, "zone cannot be null");
        this.cloudAccount = Objects.requireNonNull(cloudAccount, "cloudAccount cannot be null");
        this.revision = Objects.requireNonNull(revision, "revision cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.deployTime = Objects.requireNonNull(deployTime, "deployTime cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "deploymentMetrics cannot be null");
        this.activity = Objects.requireNonNull(activity, "activity cannot be null");
        this.quota = Objects.requireNonNull(quota, "usage cannot be null");
        this.cost = Objects.requireNonNull(cost, "cost cannot be null");
        this.dataPlaneTokens = Map.copyOf(dataPlaneTokens);
    }

    /** Returns the zone this was deployed to */
    public ZoneId zone() { return zone; }

    /** Returns the cloud account this was deployed to */
    public CloudAccount cloudAccount() { return cloudAccount; }

    /** Returns the deployed application revision */
    public RevisionId revision() { return revision; }
    
    /** Returns the deployed Vespa version */
    public Version version() { return version; }

    /** Returns the time this was deployed */
    public Instant at() { return deployTime; }

    /** Returns metrics for this */
    public DeploymentMetrics metrics() {
        return metrics;
    }

    /** Returns activity for this */
    public DeploymentActivity activity() { return activity; }

    /** Returns quota usage for this */
    public QuotaUsage quota() { return quota; }

    /** Returns cost, in dollars per hour, for this */
    public OptionalDouble cost() { return cost; }

    /** Returns the data plane token IDs referenced by this deployment, and the last update time of this token at the time of deployment. */
    public Map<TokenId, Instant> dataPlaneTokens() { return dataPlaneTokens; }

    public Deployment recordActivityAt(Instant instant) {
        return new Deployment(zone, cloudAccount, revision, version, deployTime, metrics,
                              activity.recordAt(instant, metrics), quota, cost, dataPlaneTokens);
    }

    public Deployment withMetrics(DeploymentMetrics metrics) {
        return new Deployment(zone, cloudAccount, revision, version, deployTime, metrics, activity, quota, cost, dataPlaneTokens);
    }

    public Deployment withCost(double cost) {
        if (this.cost.isPresent() && Double.compare(this.cost.getAsDouble(), cost) == 0) return this;
        return new Deployment(zone, cloudAccount, revision, version, deployTime, metrics, activity, quota, OptionalDouble.of(cost), dataPlaneTokens);
    }

    public Deployment withoutCost() {
        if (cost.isEmpty()) return this;
        return new Deployment(zone, cloudAccount, revision, version, deployTime, metrics, activity, quota, OptionalDouble.empty(), dataPlaneTokens);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Deployment that = (Deployment) o;
        return    Objects.equals(zone, that.zone)
               && Objects.equals(cloudAccount, that.cloudAccount)
               && Objects.equals(revision, that.revision)
               && Objects.equals(version, that.version)
               && Objects.equals(deployTime, that.deployTime)
               && Objects.equals(metrics, that.metrics)
               && Objects.equals(activity, that.activity)
               && Objects.equals(quota, that.quota)
               && Objects.equals(cost, that.cost)
               && Objects.equals(dataPlaneTokens, that.dataPlaneTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zone, cloudAccount, revision, version, deployTime, metrics, activity, quota, cost, dataPlaneTokens);
    }

    @Override
    public String toString() {
        return "deployment to " + zone + " of " + revision + " on version " + version + " at " + deployTime;
    }

}
