package io.github.sfesantos.portcullis;

public class IdempotencyConflictException extends PortcullisException {
    public IdempotencyConflictException(String message) {
        super(message, true);
    }

    @Override
    public int status() {
        return 409;
    }
}
