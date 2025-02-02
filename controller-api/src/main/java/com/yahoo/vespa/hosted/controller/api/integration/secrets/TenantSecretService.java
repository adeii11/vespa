// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.secrets;

import com.yahoo.config.provision.TenantName;

import java.util.List;

/**
 * @author olaa
 */
public interface TenantSecretService {

    void addSecretStore(TenantName tenant, TenantSecretStore tenantSecretStore, String externalId);

    void deleteSecretStore(TenantName tenant, TenantSecretStore tenantSecretStore);

    void cleanupSecretStores(List<TenantName> deletedTenants);

}
