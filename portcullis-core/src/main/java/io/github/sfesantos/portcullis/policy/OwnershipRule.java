package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.ForbiddenException;
import io.github.sfesantos.portcullis.OwnershipResolver;
import io.github.sfesantos.portcullis.audit.Check;

final class OwnershipRule implements AccessRule {

    private final ResourceArgument argument;
    private final String[] bypassRoles;
    private final OwnershipResolver<Object> resolver;

    OwnershipRule(ResourceArgument argument, String[] bypassRoles, OwnershipResolver<Object> resolver) {
        this.argument = argument;
        this.bypassRoles = bypassRoles.clone();
        this.resolver = resolver;
    }

    @Override
    public void verify(AccessRequest request) {
        var user = request.user();

        if (Authorities.holdsAny(user.roles(), bypassRoles)) {
            return;
        }

        var id = argument.read(request.arguments());

        if (id == null) {
            throw deny(request, argument.name() + " id is null");
        }

        if (!resolver.isOwner(user, id)) {
            throw deny(request, argument.name() + " " + id + " does not belong to the user");
        }
    }

    private static ForbiddenException deny(AccessRequest request, String reason) {
        return new ForbiddenException(Check.OWNERSHIP, request.operation() + ": " + reason);
    }

    @Override
    public boolean needsArguments() {
        return true;
    }
}
