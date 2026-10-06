package io.github.sfesantos.portcullis;

import io.github.sfesantos.portcullis.audit.AuditListener;
import io.github.sfesantos.portcullis.audit.AuditPublisher;
import io.github.sfesantos.portcullis.audit.Check;
import io.github.sfesantos.portcullis.idempotency.IdempotencyGuard;
import io.github.sfesantos.portcullis.idempotency.IdempotencyKeyProvider;
import io.github.sfesantos.portcullis.idempotency.IdempotencyStore;
import io.github.sfesantos.portcullis.idempotency.InMemoryIdempotencyStore;
import io.github.sfesantos.portcullis.policy.MethodPolicy;
import io.github.sfesantos.portcullis.policy.PolicyCompiler;
import io.github.sfesantos.portcullis.policy.Resolvers;
import io.github.sfesantos.portcullis.ratelimit.InMemoryRateLimiter;
import io.github.sfesantos.portcullis.ratelimit.RateLimiter;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AccessGuard {
    private static final Object[] NO_ARGUMENTS = new Object[0];

    private final PrincipalProvider principalProvider;
    private final PolicyCompiler compiler;
    private final AuditPublisher audit;
    private final IdempotencyGuard idempotency;

    private AccessGuard(PrincipalProvider principalProvider, PolicyCompiler compiler, AuditPublisher audit,
                        IdempotencyGuard idempotency) {
        this.principalProvider = principalProvider;
        this.compiler = compiler;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<SecurityPrincipal> currentPrincipal() {
        return principalProvider.currentPrincipal();
    }

    public IdempotencyGuard idempotency() {
        return idempotency;
    }

    public void check(Method method, Class<?> targetClass, Object[] arguments) {
        enforce(policyFor(method, targetClass), arguments);
    }

    public MethodPolicy policyFor(Method method, Class<?> targetClass) {
        try {
            return compiler.policyFor(method, targetClass);
        } catch (PolicyDefinitionException e) {
            var operation = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
            audit.denied(null, operation, Check.POLICY, e.getMessage());
            throw e;
        }
    }

    public void enforce(MethodPolicy policy, Object[] arguments) {
        if (policy.isUnrestricted()) {
            return;
        }

        var user = currentPrincipal().orElse(null);

        try {
            policy.verify(user, arguments == null ? NO_ARGUMENTS : arguments);
        } catch (RuntimeException e) {
            audit.denied(user, policy.operation(), e);
            throw e;
        }

        audit.granted(user, policy.operation());
    }

    public static final class Builder {
        private PrincipalProvider principalProvider = PortcullisContext::current;
        private final Map<Class<?>, OwnershipResolver<?>> ownership = new HashMap<>();
        private final Map<Class<?>, TenantResolver<?>> tenancy = new HashMap<>();
        private final List<AuditListener> auditListeners = new ArrayList<>();
        private RateLimiter rateLimiter;
        private IdempotencyStore idempotencyStore;
        private IdempotencyKeyProvider idempotencyKeyProvider = IdempotencyKeyProvider.NONE;
        private Duration idempotencyLease = Duration.ofMinutes(1);
        private Clock clock = Clock.systemUTC();

        Builder() {
        }

        public Builder principalProvider(PrincipalProvider provider) {
            this.principalProvider = Objects.requireNonNull(provider, "provider");

            return this;
        }

        public <ID> Builder ownership(Class<?> resourceType, OwnershipResolver<ID> resolver) {
            ownership.put(Objects.requireNonNull(resourceType, "resourceType"), Objects.requireNonNull(resolver, "resolver"));

            return this;
        }

        public <ID> Builder tenancy(Class<?> resourceType, TenantResolver<ID> resolver) {
            tenancy.put(Objects.requireNonNull(resourceType, "resourceType"), Objects.requireNonNull(resolver, "resolver"));

            return this;
        }

        public Builder rateLimiter(RateLimiter rateLimiter) {
            this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");

            return this;
        }

        public Builder idempotencyStore(IdempotencyStore store) {
            this.idempotencyStore = Objects.requireNonNull(store, "store");

            return this;
        }

        public Builder idempotencyKeyProvider(IdempotencyKeyProvider provider) {
            this.idempotencyKeyProvider = Objects.requireNonNull(provider, "provider");

            return this;
        }

        // How long a running request holds its key; a crashed instance frees it after this.
        public Builder idempotencyLease(Duration lease) {
            this.idempotencyLease = Objects.requireNonNull(lease, "lease");

            return this;
        }

        public Builder auditListener(AuditListener listener) {
            auditListeners.add(Objects.requireNonNull(listener, "listener"));

            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");

            return this;
        }

        public AccessGuard build() {
            var principals = nullSafe(principalProvider);
            var limiter = rateLimiter != null ? rateLimiter : new InMemoryRateLimiter();
            var compiler = new PolicyCompiler(new Resolvers(ownership, tenancy, limiter));
            var store = idempotencyStore != null ? idempotencyStore : new InMemoryIdempotencyStore();
            var idempotency = new IdempotencyGuard(store, idempotencyKeyProvider, principals, idempotencyLease);

            return new AccessGuard(principals, compiler, new AuditPublisher(auditListeners, clock), idempotency);
        }

        // A user supplied provider may return null instead of Optional.empty().
        private static PrincipalProvider nullSafe(PrincipalProvider provider) {
            return () -> Objects.requireNonNullElse(provider.currentPrincipal(), Optional.empty());
        }

        public AccessGuard install() {
            var guard = build();
            Portcullis.install(guard);

            return guard;
        }
    }
}
