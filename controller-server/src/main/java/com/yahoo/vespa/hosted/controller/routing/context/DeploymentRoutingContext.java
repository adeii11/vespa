// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing.context;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.vespa.hosted.controller.LockedApplication;
import com.yahoo.vespa.hosted.controller.RoutingController;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyId;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A deployment routing context, which extends {@link RoutingContext} to support routing configuration of a deployment.
 *
 * @author mpolden
 */
public abstract class DeploymentRoutingContext implements RoutingContext {

    final DeploymentId deployment;
    final RoutingController controller;
    final RoutingMethod method;

    public DeploymentRoutingContext(DeploymentId deployment, RoutingMethod method, RoutingController controller) {
        this.deployment = Objects.requireNonNull(deployment);
        this.controller = Objects.requireNonNull(controller);
        this.method = Objects.requireNonNull(method);
    }

    /**
     * Prepare routing configuration for the deployment in this context
     *
     * @return the container endpoints relevant for this deployment, as declared in deployment spec
     */
    public final Set<ContainerEndpoint> prepare(LockedApplication application) {
        return controller.containerEndpointsOf(application, deployment.applicationId().instance(), deployment.zoneId());
    }

    /** Configure routing for the deployment in this context, using given deployment spec */
    public final void configure(DeploymentSpec deploymentSpec) {
        controller.policies().refresh(deployment, deploymentSpec);
    }

    /** Routing method of this context */
    public final RoutingMethod routingMethod() {
        return method;
    }

    /** Read the routing policy for given cluster in this deployment */
    public final Optional<RoutingPolicy> routingPolicy(ClusterSpec.Id cluster) {
        RoutingPolicyId id = new RoutingPolicyId(deployment.applicationId(), cluster, deployment.zoneId());
        return controller.policies().read(deployment).of(id);
    }

    /**
     * Extension of a {@link DeploymentRoutingContext} for deployments using either {@link RoutingMethod#shared} or
     * {@link RoutingMethod#sharedLayer4} routing.
     */
    public static class SharedDeploymentRoutingContext extends DeploymentRoutingContext {

        private final Clock clock;
        private final ConfigServer configServer;
        private final boolean changeAllUpstreams;

        public SharedDeploymentRoutingContext(DeploymentId deployment, RoutingController controller, ConfigServer configServer, Clock clock, boolean changeAllUpstreams) {
            super(deployment, RoutingMethod.shared, controller);
            this.clock = Objects.requireNonNull(clock);
            this.configServer = Objects.requireNonNull(configServer);
            this.changeAllUpstreams = changeAllUpstreams;
        }

        @Override
        public void setRoutingStatus(RoutingStatus.Value value, RoutingStatus.Agent agent) {
            if (!changeAllUpstreams) {
                setLegacyRoutingStatus(value, agent);
                return;
            }
            EndpointStatus newStatus = new EndpointStatus(value == RoutingStatus.Value.in
                                                                  ? EndpointStatus.Status.in
                                                                  : EndpointStatus.Status.out,
                                                          "",
                                                          agent.name(),
                                                          clock.instant().getEpochSecond());
            try {
                configServer.setGlobalRotationStatus(deployment, upstreamNames(), newStatus);
            } catch (Exception e) {
                throw new RuntimeException("Failed to change rotation status of " + deployment, e);
            }
        }

        @Override
        public RoutingStatus routingStatus() {
            if (!changeAllUpstreams) {
                return legacyRoutingStatus();
            }

            // In a given deployment, all upstreams (clusters) share the same status, so we can query using any
            // upstream name
            String upstreamName = upstreamNames().get(0);
            EndpointStatus status = configServer.getGlobalRotationStatus(deployment, upstreamName);
            RoutingStatus.Agent agent;
            try {
                agent = RoutingStatus.Agent.valueOf(status.getAgent().toLowerCase());
            } catch (IllegalArgumentException e) {
                agent = RoutingStatus.Agent.unknown;
            }
            return new RoutingStatus(status.getStatus() == EndpointStatus.Status.in
                                             ? RoutingStatus.Value.in
                                             : RoutingStatus.Value.out,
                                     agent,
                                     Instant.ofEpochSecond(status.getEpoch()));
        }

        private List<String> upstreamNames() {
            List<String> upstreamNames = controller.readEndpointsOf(deployment)
                                                   .scope(Endpoint.Scope.zone)
                                                   .shared()
                                                   .mapToList(endpoint -> endpoint.upstreamName(deployment));
            if (upstreamNames.isEmpty()) {
                throw new IllegalArgumentException("No upstream names found for " + deployment);
            }
            return upstreamNames;
        }

        private void setLegacyRoutingStatus(RoutingStatus.Value value, RoutingStatus.Agent agent) {
            EndpointStatus newStatus = new EndpointStatus(value == RoutingStatus.Value.in
                                                                  ? EndpointStatus.Status.in
                                                                  : EndpointStatus.Status.out,
                                                          "",
                                                          agent.name(),
                                                          clock.instant().getEpochSecond());
            primaryEndpoint().ifPresent(endpoint -> {
                try {
                    configServer.setGlobalRotationStatus(deployment, List.of(endpoint.upstreamName(deployment)), newStatus);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to set rotation status of " + endpoint + " in " + deployment, e);
                }
            });
        }

        private RoutingStatus legacyRoutingStatus() {
            Optional<EndpointStatus> status = primaryEndpoint().map(endpoint -> {
                var upstreamName = endpoint.upstreamName(deployment);
                return configServer.getGlobalRotationStatus(deployment, upstreamName);
            });
            if (status.isEmpty()) return RoutingStatus.DEFAULT;
            RoutingStatus.Agent agent;
            try {
                agent = RoutingStatus.Agent.valueOf(status.get().getAgent().toLowerCase());
            } catch (IllegalArgumentException e) {
                agent = RoutingStatus.Agent.unknown;
            }
            return new RoutingStatus(status.get().getStatus() == EndpointStatus.Status.in
                                             ? RoutingStatus.Value.in
                                             : RoutingStatus.Value.out,
                                     agent,
                                     Instant.ofEpochSecond(status.get().getEpoch()));
        }

        private Optional<Endpoint> primaryEndpoint() {
            return controller.readDeclaredEndpointsOf(deployment.applicationId())
                             .requiresRotation()
                             .primary();
        }

    }

    /**
     * Implementation of a {@link DeploymentRoutingContext} for deployments using {@link RoutingMethod#exclusive}
     * routing.
     */
    public static class ExclusiveDeploymentRoutingContext extends DeploymentRoutingContext {

        public ExclusiveDeploymentRoutingContext(DeploymentId deployment, RoutingController controller) {
            super(deployment, RoutingMethod.exclusive, controller);
        }

        @Override
        public void setRoutingStatus(RoutingStatus.Value value, RoutingStatus.Agent agent) {
            controller.policies().setRoutingStatus(deployment, value, agent);
        }

        @Override
        public RoutingStatus routingStatus() {
            // Status for a deployment applies to all clusters within the deployment, so we use the status from the
            // first matching policy here
            return controller.policies().read(deployment)
                             .first()
                             .map(RoutingPolicy::status)
                             .map(RoutingPolicy.Status::routingStatus)
                             .orElse(RoutingStatus.DEFAULT);
        }

    }

}
