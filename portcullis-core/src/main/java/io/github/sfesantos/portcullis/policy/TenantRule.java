package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.ForbiddenException;
import io.github.sfesantos.portcullis.TenantResolver;
import io.github.sfesantos.portcullis.audit.Check;

final class TenantRule implements AccessRule {

    private final ResourceArgument argument;
    private final String[] bypassRoles;
    private final TenantResolver<Object> resolver;

    TenantRule(ResourceArgument argument, String[] bypassRoles, TenantResolver<Object> resolver) {
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

        var tenant = user.tenantId()
                .orElseThrow(() -> deny(request, "user has no tenant"));
        var id = argument.read(request.arguments());

        if (id == null) {
            throw deny(request, argument.name() + " id is null");
        }

        if (!resolver.belongsToTenant(tenant, id)) {
            throw deny(request, argument.name() + " " + id + " is outside the user's tenant");
        }
    }

    private static ForbiddenException deny(AccessRequest request, String reason) {
        return new ForbiddenException(Check.TENANT, request.operation() + ": " + reason);
    }

    @Override
    public boolean needsArguments() {
        return true;
    }
}
