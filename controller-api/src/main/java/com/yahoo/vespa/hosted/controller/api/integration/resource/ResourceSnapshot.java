// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents the resources allocated to a deployment at a specific point in time.
 *
 * @author olaa
 */
public class ResourceSnapshot {

    private static final NodeResources zero = new NodeResources(
            0, 0, 0, 0,
            NodeResources.DiskSpeed.any,
            NodeResources.StorageType.any,
            NodeResources.Architecture.any,
            NodeResources.GpuResources.zero());

    private final ApplicationId applicationId;
    private final NodeResources resources;
    private final Instant timestamp;
    private final ZoneId zoneId;
    private final int majorVersion;
    private final CloudAccount account;

    public ResourceSnapshot(ApplicationId applicationId, NodeResources resources, Instant timestamp, ZoneId zoneId, int majorVersion, CloudAccount account) {
        this.applicationId = applicationId;
        this.resources = resources;
        this.timestamp = timestamp;
        this.zoneId = zoneId;
        this.majorVersion = majorVersion;
        this.account = account;
    }

    public static ResourceSnapshot from(ApplicationId applicationId, int nodes, NodeResources resources, Instant timestamp, ZoneId zoneId) {
        return new ResourceSnapshot(applicationId, resources.multipliedBy(nodes), timestamp, zoneId, 0, CloudAccount.empty);
    }

    public static ResourceSnapshot from(List<Node> nodes, Instant timestamp, ZoneId zoneId) {
        var application = exactlyOne("application", nodes.stream()
                .filter(node -> node.owner().isPresent())
                .map(node -> node.owner().get())
                .collect(Collectors.toSet()));

        var version = exactlyOne("version", nodes.stream()
                .map(n -> n.wantedVersion().getMajor())
                .collect(Collectors.toSet()));

        var account = exactlyOne("account", nodes.stream()
                .map(Node::cloudAccount)
                .collect(Collectors.toSet()));

        var resources = nodes.stream()
                .map(Node::resources)
                .reduce(zero, ResourceSnapshot::addResources);

        return new ResourceSnapshot(application, resources, timestamp, zoneId, version, account);
    }

    public ApplicationId getApplicationId() {
        return applicationId;
    }

    public NodeResources resources() {
        return resources;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public CloudAccount getAccount() {
        return account;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceSnapshot)) return false;

        ResourceSnapshot other = (ResourceSnapshot) o;
        return this.applicationId.equals(other.applicationId) &&
                this.resources.equals(other.resources) &&
                this.timestamp.equals(other.timestamp) &&
                this.zoneId.equals(other.zoneId) &&
                this.majorVersion == other.majorVersion;
    }

    @Override
    public int hashCode(){
        return Objects.hash(applicationId, resources, timestamp, zoneId, majorVersion);
    }

    /* This function does pretty much the same thing as NodeResources::add, but it allows adding resources
     * where some dimensions that are not relevant for billing (yet) are not the same.
     *
     * TODO: Make this code respect all dimensions.
     */
    private static NodeResources addResources(NodeResources a, NodeResources b) {
        if (a.architecture() != b.architecture() && a.architecture() != NodeResources.Architecture.any && b.architecture() != NodeResources.Architecture.any) {
            throw new IllegalArgumentException(a + " and " + b + " are not interchangeable for resource snapshots");
        }
        return new NodeResources(
                a.vcpu() + b.vcpu(),
                a.memoryGb() + b.memoryGb(),
                a.diskGb() + b.diskGb(),
                0,
                NodeResources.DiskSpeed.any,
                NodeResources.StorageType.any,
                a.architecture() == NodeResources.Architecture.any ? b.architecture() : a.architecture(),
                a.gpuResources().plus(b.gpuResources()));
    }

    private static <T> T exactlyOne(String resource, Collection<T> collection) {
        if (collection.size() != 1) throw new IllegalArgumentException("More than one '" + resource + "', was: " + collection.size());
        return collection.iterator().next();
    }
}
