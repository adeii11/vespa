// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;

import java.util.Objects;
import java.util.Optional;

/**
 * @author gv
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_ABSENT)
public class TenantUpdateOptions {
    public final Property property;
    public final Optional<AthenzDomain> athensDomain;

    @JsonCreator
    public TenantUpdateOptions(@JsonProperty("property") Property property,
                               @JsonProperty("athensDomain") Optional<AthenzDomain> athensDomain) {
        this.property = property;
        this.athensDomain = athensDomain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantUpdateOptions that = (TenantUpdateOptions) o;
        return Objects.equals(property, that.property) &&
                Objects.equals(athensDomain, that.athensDomain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, athensDomain);
    }

    @Override
    public String toString() {
        return "TenantUpdateOptions{" +
                "property=" + property +
                ", athensDomain=" + athensDomain +
                '}';
    }
}
