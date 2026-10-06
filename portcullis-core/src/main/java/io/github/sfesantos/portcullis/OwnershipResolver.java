package io.github.sfesantos.portcullis;

@FunctionalInterface
public interface OwnershipResolver<ID> {
    boolean isOwner(SecurityPrincipal user, ID resourceId);
}
