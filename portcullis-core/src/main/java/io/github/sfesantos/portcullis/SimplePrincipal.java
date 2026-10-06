package io.github.sfesantos.portcullis;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record SimplePrincipal(String id, Set<String> roles, Set<String> permissions, String tenant)
        implements SecurityPrincipal {

    public SimplePrincipal {
        Objects.requireNonNull(id, "id");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static SimplePrincipal of(String id) {
        return new SimplePrincipal(id, Set.of(), Set.of(), null);
    }

    public SimplePrincipal withRoles(String... roles) {
        return new SimplePrincipal(id, Set.of(roles), permissions, tenant);
    }

    public SimplePrincipal withPermissions(String... permissions) {
        return new SimplePrincipal(id, roles, Set.of(permissions), tenant);
    }

    public SimplePrincipal withTenant(String tenant) {
        return new SimplePrincipal(id, roles, permissions, tenant);
    }

    @Override
    public Optional<String> tenantId() {
        return Optional.ofNullable(tenant);
    }
}
