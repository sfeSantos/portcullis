package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.UnauthenticatedException;

final class AuthenticationRule implements AccessRule {
    static final AuthenticationRule INSTANCE = new AuthenticationRule();

    private AuthenticationRule() {
    }

    @Override
    public void verify(AccessRequest request) {
        if (request.user() == null) {
            throw new UnauthenticatedException(request.operation() + ": authentication required");
        }
    }
}
