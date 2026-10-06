package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.OwnershipResolver;
import io.github.sfesantos.portcullis.PolicyDefinitionException;
import io.github.sfesantos.portcullis.TenantResolver;
import io.github.sfesantos.portcullis.annotation.Authenticated;
import io.github.sfesantos.portcullis.annotation.OwnedBy;
import io.github.sfesantos.portcullis.annotation.PublicAccess;
import io.github.sfesantos.portcullis.annotation.RateLimit;
import io.github.sfesantos.portcullis.annotation.RequiresPermission;
import io.github.sfesantos.portcullis.annotation.RequiresRole;
import io.github.sfesantos.portcullis.annotation.ResourceId;
import io.github.sfesantos.portcullis.annotation.SameTenant;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class PolicyCompiler {
    private final Resolvers resolvers;
    private final ClassValue<ConcurrentHashMap<Method, MethodPolicy>> cache = new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<Method, MethodPolicy> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    public PolicyCompiler(Resolvers resolvers) {
        this.resolvers = resolvers;
    }

    public MethodPolicy policyFor(Method method, Class<?> targetClass) {
        var policies = cache.get(targetClass == null ? method.getDeclaringClass() : targetClass);
        var policy = policies.get(method);

        if (policy == null) {
            policy = policies.computeIfAbsent(method, m -> compile(new AnnotatedMethod(m, targetClass)));
        }

        return policy;
    }

    private MethodPolicy compile(AnnotatedMethod method) {
        var operation = method.operation();
        var isPublic = method.onMethod(PublicAccess.class);

        var roles = isPublic ? method.onMethodOnly(RequiresRole.class) : method.onMethodOrType(RequiresRole.class);
        var permissions = isPublic
                ? method.onMethodOnly(RequiresPermission.class)
                : method.onMethodOrType(RequiresPermission.class);
        var authenticated = isPublic
                ? method.onMethodOnly(Authenticated.class)
                : method.onMethodOrType(Authenticated.class);
        var rateLimit = method.onMethodOrType(RateLimit.class);
        var tenants = method.repeatable(SameTenant.class);
        var owners = method.repeatable(OwnedBy.class);

        var needsUser = authenticated != null || roles != null || permissions != null
                || tenants.length > 0 || owners.length > 0;
        if (isPublic && needsUser) {
            throw new PolicyDefinitionException(operation + ": @PublicAccess cannot be combined with checks that need a user");
        }

        // Cheapest checks first, so a denied call never reaches a resolver.
        var rules = new ArrayList<AccessRule>();

        if (needsUser) {
            rules.add(AuthenticationRule.INSTANCE);
        }

        if (rateLimit != null) {
            rules.add(rateLimitRule(operation, method, rateLimit));
        }

        if (roles != null) {
            rules.add(AuthorityRule.roles(nonEmpty(operation, "@RequiresRole", roles.value()), roles.match()));
        }

        if (permissions != null) {
            rules.add(AuthorityRule.permissions(
                    nonEmpty(operation, "@RequiresPermission", permissions.value()), permissions.match()));
        }

        if (tenants.length > 0 || owners.length > 0) {
            addResourceRules(rules, operation, method, tenants, owners);
        }

        return rules.isEmpty()
                ? MethodPolicy.unrestricted(operation)
                : new MethodPolicy(operation, rules.toArray(AccessRule[]::new));
    }

    private void addResourceRules(List<AccessRule> rules, String operation, AnnotatedMethod method,
                                  SameTenant[] tenants, OwnedBy[] owners) {
        var resourceIds = method.resourceIds();

        for (var tenant : tenants) {
            var argument = argument(operation, "@SameTenant", tenant.value(), resourceIds);
            rules.add(new TenantRule(argument, tenant.bypassRoles(), tenantResolver(operation, tenant.value())));
        }

        for (var owner : owners) {
            var argument = argument(operation, "@OwnedBy", owner.value(), resourceIds);
            rules.add(new OwnershipRule(argument, owner.bypassRoles(), ownershipResolver(operation, owner.value())));
        }
    }

    private RateLimitRule rateLimitRule(String operation, AnnotatedMethod method, RateLimit limit) {
        if (limit.requests() <= 0 || limit.window() <= 0) {
            throw new PolicyDefinitionException(operation + ": @RateLimit needs positive requests and window");
        }

        var key = limit.key().isBlank() ? method.qualifiedName() : limit.key();
        var window = Duration.of(limit.window(), limit.unit());

        return new RateLimitRule(key, limit.requests(), window, resolvers.rateLimiter());
    }

    @SuppressWarnings("unchecked")
    private OwnershipResolver<Object> ownershipResolver(String operation, Class<?> type) {
        var resolver = resolvers.ownership().get(type);

        if (resolver == null) {
            throw new PolicyDefinitionException(operation + ": no OwnershipResolver registered for " + type.getName());
        }

        return (OwnershipResolver<Object>) resolver;
    }

    @SuppressWarnings("unchecked")
    private TenantResolver<Object> tenantResolver(String operation, Class<?> type) {
        var resolver = resolvers.tenancy().get(type);

        if (resolver == null) {
            throw new PolicyDefinitionException(operation + ": no TenantResolver registered for " + type.getName());
        }

        return (TenantResolver<Object>) resolver;
    }

    private static String[] nonEmpty(String operation, String annotation, String[] values) {
        if (values.length == 0) {
            throw new PolicyDefinitionException(operation + ": " + annotation + " needs at least one value");
        }

        return values;
    }

    private static ResourceArgument argument(String operation, String annotation, Class<?> type, ResourceId[] ids) {
        var typed = -1;
        var untyped = -1;
        var untypedCount = 0;

        for (var i = 0; i < ids.length; i++) {
            if (ids[i] == null) {
                continue;
            }

            if (ids[i].value() == type) {
                if (typed >= 0) {
                    throw ambiguous(operation, type);
                }

                typed = i;
            } else if (ids[i].value() == Void.class) {
                untyped = i;
                untypedCount++;
            }
        }

        if (typed >= 0) {
            return new ResourceArgument(type, typed);
        }

        if (untypedCount > 1) {
            throw ambiguous(operation, type);
        }

        if (untyped < 0) {
            throw new PolicyDefinitionException(operation + ": " + annotation + "(" + type.getSimpleName()
                    + ".class) needs a parameter annotated with @ResourceId");
        }

        return new ResourceArgument(type, untyped);
    }

    private static PolicyDefinitionException ambiguous(String operation, Class<?> type) {
        var name = type.getSimpleName();

        return new PolicyDefinitionException(operation + ": several @ResourceId parameters match " + name
                + "; use @ResourceId(" + name + ".class)");
    }
}
