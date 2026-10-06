package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.SecurityPrincipal;

public record AccessRequest(String operation, SecurityPrincipal user, Object[] arguments) {
}
