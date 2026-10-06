package io.github.sfesantos.portcullis.idempotency;

import io.github.sfesantos.portcullis.IdempotencyConflictException;
import io.github.sfesantos.portcullis.InvalidIdempotencyKeyException;
import io.github.sfesantos.portcullis.PolicyDefinitionException;
import io.github.sfesantos.portcullis.SecurityPrincipal;
import io.github.sfesantos.portcullis.SimplePrincipal;
import io.github.sfesantos.portcullis.annotation.IdempotencyKey;
import io.github.sfesantos.portcullis.annotation.Idempotent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyGuardTest {
    static class Payments {
        @Idempotent
        public String pay(@IdempotencyKey String key, long amount) {
            return "paid";
        }

        @Idempotent(required = false)
        public String optional(@IdempotencyKey String key) {
            return "ok";
        }

        @Idempotent
        public String fromHeader() {
            return "ok";
        }

        @Idempotent
        public void twoKeys(@IdempotencyKey String a, @IdempotencyKey String b) {
        }
    }

    private final AtomicLong now = new AtomicLong();
    private final AtomicReference<SecurityPrincipal> user = new AtomicReference<>(SimplePrincipal.of("alice"));
    private final AtomicReference<String> header = new AtomicReference<>();
    private final IdempotencyGuard guard = new IdempotencyGuard(new InMemoryIdempotencyStore(now::get),
            () -> Optional.ofNullable(header.get()), () -> Optional.ofNullable(user.get()), Duration.ofMinutes(1));
    private final AtomicInteger executions = new AtomicInteger();

    private Object call(String method, Object... args) throws Throwable {
        return guard.execute(method(method), Payments.class, args, () -> {
            executions.incrementAndGet();

            return "result " + executions.get();
        });
    }

    private static Method method(String name) {
        for (var m : Payments.class.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }

        throw new IllegalArgumentException(name);
    }

    @Test
    void repeatedKeyReturnsTheFirstResultWithoutRunningAgain() throws Throwable {
        assertThat(call("pay", "k1", 100L)).isEqualTo("result 1");
        assertThat(call("pay", "k1", 100L)).isEqualTo("result 1");
        assertThat(executions).hasValue(1);
    }

    @Test
    void differentKeysRunSeparately() throws Throwable {
        call("pay", "k1", 100L);
        call("pay", "k2", 100L);
        assertThat(executions).hasValue(2);
    }

    @Test
    void keysAreScopedPerUser() throws Throwable {
        call("pay", "k1", 100L);
        user.set(SimplePrincipal.of("bob"));
        assertThat(call("pay", "k1", 100L)).isEqualTo("result 2");
    }

    @Test
    void duplicateWhileRunningIs409() {
        assertThatThrownBy(() -> guard.execute(method("pay"), Payments.class, new Object[] {"k1", 1L},
                () -> call("pay", "k1", 1L)))
                .isInstanceOf(IdempotencyConflictException.class)
                .satisfies(e -> assertThat(((IdempotencyConflictException) e).status()).isEqualTo(409));
    }

    @Test
    void failureReleasesTheKeySoTheClientCanRetry() throws Throwable {
        assertThatThrownBy(() -> guard.execute(method("pay"), Payments.class, new Object[] {"k1", 1L}, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(call("pay", "k1", 1L)).isEqualTo("result 1");
    }

    @Test
    void resultExpiresAfterTtl() throws Throwable {
        call("pay", "k1", 1L);
        now.addAndGet(Duration.ofHours(25).toNanos());
        call("pay", "k1", 1L);
        assertThat(executions).hasValue(2);
    }

    @Test
    void crashedRequestFreesTheKeyAfterTheLease() throws Throwable {
        var store = new InMemoryIdempotencyStore(now::get);
        store.reserve("Payments#pay:alice:k1", Duration.ofMinutes(1), String.class);
        var crashedGuard = new IdempotencyGuard(store, IdempotencyKeyProvider.NONE,
                () -> Optional.of(SimplePrincipal.of("alice")), Duration.ofMinutes(1));
        now.addAndGet(Duration.ofMinutes(2).toNanos());
        assertThat(crashedGuard.execute(method("pay"), Payments.class, new Object[] {"k1", 1L}, () -> "retried"))
                .isEqualTo("retried");
    }

    @Test
    void missingKeyIs400WhenRequired() {
        assertThatThrownBy(() -> call("pay", null, 1L))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .satisfies(e -> assertThat(((InvalidIdempotencyKeyException) e).status()).isEqualTo(400));
    }

    @Test
    void missingKeyJustRunsWhenOptional() throws Throwable {
        call("optional", (Object) null);
        call("optional", (Object) null);
        assertThat(executions).hasValue(2);
    }

    @Test
    void tooLongKeyIs400() {
        assertThatThrownBy(() -> call("pay", "x".repeat(129), 1L)).isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void keyCanComeFromTheProvider() throws Throwable {
        header.set("from-header");
        call("fromHeader");
        call("fromHeader");
        assertThat(executions).hasValue(1);
    }

    @Test
    void twoKeyParametersAreRejected() {
        assertThatThrownBy(() -> call("twoKeys", "a", "b")).isInstanceOf(PolicyDefinitionException.class);
    }
}
