package io.github.sfesantos.portcullis.audit;

import io.github.sfesantos.portcullis.SecurityPrincipal;

import java.time.Instant;
import java.util.Optional;

public record AccessEvent(Instant timestamp,
                          SecurityPrincipal principal,
                          String operation,
                          boolean granted,
                          Check deniedBy,
                          String reason) {
    public Optional<SecurityPrincipal> user() {
        return Optional.ofNullable(principal);
    }
}
