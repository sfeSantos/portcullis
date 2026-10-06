package io.github.sfesantos.portcullis;

@FunctionalInterface
public interface TenantResolver<ID> {

    boolean belongsToTenant(String tenantId, ID resourceId);
}
