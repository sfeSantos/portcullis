package io.github.sfesantos.portcullis;

import java.util.Optional;

@FunctionalInterface
public interface AnonymousKeyProvider {

    AnonymousKeyProvider NONE = Optional::empty;
    Optional<String> anonymousKey();
}
