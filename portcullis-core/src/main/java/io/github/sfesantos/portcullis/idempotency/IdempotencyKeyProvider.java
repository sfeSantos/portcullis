package io.github.sfesantos.portcullis.idempotency;

import java.util.Optional;

@FunctionalInterface
public interface IdempotencyKeyProvider {
    IdempotencyKeyProvider NONE = Optional::empty;

    Optional<String> currentKey();
}
