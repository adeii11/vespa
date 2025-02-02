// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

/**
 * @author jonmv
 */
public record QuotaUsage(double rate) {

    public static final QuotaUsage zero = new QuotaUsage(0);

}
