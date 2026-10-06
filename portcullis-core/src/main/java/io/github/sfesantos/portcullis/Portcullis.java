package io.github.sfesantos.portcullis;

import java.util.Objects;
import java.util.Optional;

public final class Portcullis {

    private static volatile AccessGuard guard = AccessGuard.builder().build();

    private Portcullis() {}

    public static AccessGuard.Builder configure() {
        return AccessGuard.builder();
    }

    public static AccessGuard guard() {
        return guard;
    }

    public static void install(AccessGuard newGuard) {
        guard = Objects.requireNonNull(newGuard, "guard");
    }

    public static void reset() {
        guard = AccessGuard.builder().build();
    }

    public static Optional<SecurityPrincipal> currentPrincipal() {
        return guard.currentPrincipal();
    }

    public static SecurityPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new UnauthenticatedException("authentication required"));
    }
}
