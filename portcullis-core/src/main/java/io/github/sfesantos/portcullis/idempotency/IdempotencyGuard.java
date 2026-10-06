package io.github.sfesantos.portcullis.idempotency;

import io.github.sfesantos.portcullis.IdempotencyConflictException;
import io.github.sfesantos.portcullis.InvalidIdempotencyKeyException;
import io.github.sfesantos.portcullis.PolicyDefinitionException;
import io.github.sfesantos.portcullis.PrincipalProvider;
import io.github.sfesantos.portcullis.SecurityPrincipal;
import io.github.sfesantos.portcullis.annotation.IdempotencyKey;
import io.github.sfesantos.portcullis.annotation.Idempotent;

import java.lang.System.Logger.Level;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public final class IdempotencyGuard {
    public static final int MAX_KEY_LENGTH = 128;

    private static final String ANONYMOUS = "anonymous";
    private static final System.Logger LOG = System.getLogger(IdempotencyGuard.class.getName());

    private final IdempotencyStore store;
    private final IdempotencyKeyProvider keyProvider;
    private final PrincipalProvider principalProvider;
    private final Duration lease;
    private final ClassValue<ConcurrentHashMap<Method, IdempotencyPolicy>> policies = new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<Method, IdempotencyPolicy> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    public IdempotencyGuard(IdempotencyStore store, IdempotencyKeyProvider keyProvider,
                            PrincipalProvider principalProvider, Duration lease) {
        this.store = store;
        this.keyProvider = keyProvider;
        this.principalProvider = principalProvider;
        this.lease = lease;
    }

    public Object execute(Method method, Class<?> targetClass, Object[] arguments, Invocation invocation) throws Throwable {
        var policy = policyFor(method, targetClass);
        var clientKey = clientKey(policy, arguments);

        if (clientKey == null) {
            return invocation.proceed();
        }

        var key = policy.operation() + ':' + subject() + ':' + clientKey;

        return switch (store.reserve(key, lease, policy.resultType())) {
            case Reservation.Acquired ignored -> runOnce(key, policy, invocation);
            case Reservation.InProgress ignored -> throw new IdempotencyConflictException(
                    policy.operation() + ": a request with this idempotency key is still running");
            case Reservation.Completed completed -> completed.result();
        };
    }

    private Object runOnce(String key, IdempotencyPolicy policy, Invocation invocation) throws Throwable {
        Object result;

        try {
            result = invocation.proceed();
        } catch (Throwable failure) {
            store.release(key);
            throw failure;
        }

        try {
            store.complete(key, result, policy.ttl());
        } catch (RuntimeException e) {
            // The work is done; failing now would make the client retry it. The lease keeps duplicates out meanwhile.
            LOG.log(Level.WARNING, "could not store idempotent result for " + policy.operation(), e);
        }

        return result;
    }

    private String clientKey(IdempotencyPolicy policy, Object[] arguments) {
        var key = policy.keyFrom(arguments);

        if (key == null) {
            key = keyProvider.currentKey().orElse(null);
        }

        if (key == null || key.isBlank()) {
            if (policy.required()) {
                throw new InvalidIdempotencyKeyException(policy.operation() + ": idempotency key is required");
            }

            return null;
        }

        if (key.length() > MAX_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException(
                    policy.operation() + ": idempotency key longer than " + MAX_KEY_LENGTH + " characters");
        }

        return key;
    }

    private String subject() {
        return principalProvider.currentPrincipal().map(SecurityPrincipal::id).orElse(ANONYMOUS);
    }

    private IdempotencyPolicy policyFor(Method method, Class<?> targetClass) {
        var cache = policies.get(targetClass == null ? method.getDeclaringClass() : targetClass);
        var policy = cache.get(method);

        if (policy == null) {
            policy = cache.computeIfAbsent(method, IdempotencyGuard::compile);
        }

        return policy;
    }

    private static IdempotencyPolicy compile(Method method) {
        var operation = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
        var annotation = method.getAnnotation(Idempotent.class);

        if (annotation == null) {
            throw new PolicyDefinitionException(operation + ": not annotated with @Idempotent");
        }

        if (annotation.ttl() <= 0) {
            throw new PolicyDefinitionException(operation + ": @Idempotent needs a positive ttl");
        }

        var ttl = Duration.of(annotation.ttl(), annotation.unit());

        return new IdempotencyPolicy(operation, ttl, annotation.required(), keyIndex(operation, method),
                method.getGenericReturnType());
    }

    private static int keyIndex(String operation, Method method) {
        var index = IdempotencyPolicy.NO_KEY_PARAMETER;
        var parameters = method.getParameters();

        for (var i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(IdempotencyKey.class)) {
                if (index != IdempotencyPolicy.NO_KEY_PARAMETER) {
                    throw new PolicyDefinitionException(operation + ": only one parameter may be annotated with @IdempotencyKey");
                }

                index = i;
            }
        }

        return index;
    }
}
