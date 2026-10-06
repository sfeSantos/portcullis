package io.github.sfesantos.portcullis;

import java.util.Optional;
import java.util.Set;

public interface SecurityPrincipal {
    String id();

    default Set<String> roles() {
        return Set.of();
    }

    default Set<String> permissions() {
        return Set.of();
    }

    default Optional<String> tenantId() {
        return Optional.empty();
    }

    default boolean hasRole(String role) {
        return roles().contains(role);
    }

    default boolean hasPermission(String permission) {
        return permissions().contains(permission);
    }
}
