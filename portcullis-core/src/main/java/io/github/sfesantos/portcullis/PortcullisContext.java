package io.github.sfesantos.portcullis;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class PortcullisContext {

    private static final ThreadLocal<SecurityPrincipal> CURRENT = new ThreadLocal<>();

    private PortcullisContext() {}

    public static Optional<SecurityPrincipal> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Scope bind(SecurityPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        var previous = CURRENT.get();
        CURRENT.set(principal);

        return () -> restore(previous);
    }

    public static void runAs(SecurityPrincipal principal, Runnable action) {
        try (var ignored = bind(principal)) {
            action.run();
        }
    }

    public static <T> T callAs(SecurityPrincipal principal, Supplier<T> action) {
        try (var ignored = bind(principal)) {
            return action.get();
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static void restore(SecurityPrincipal previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
