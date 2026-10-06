package io.github.sfesantos.portcullis;

import java.util.Optional;

@FunctionalInterface
public interface PrincipalProvider {

    Optional<SecurityPrincipal> currentPrincipal();
}
