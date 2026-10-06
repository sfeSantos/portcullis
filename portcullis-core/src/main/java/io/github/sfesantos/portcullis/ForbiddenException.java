package io.github.sfesantos.portcullis;

import io.github.sfesantos.portcullis.audit.Check;

public class ForbiddenException extends PortcullisException {
    private final transient Check check;

    public ForbiddenException(Check check, String message) {
        super(message, true);
        this.check = check;
    }

    public Check check() {
        return check;
    }

    @Override
    public int status() {
        return 403;
    }
}
