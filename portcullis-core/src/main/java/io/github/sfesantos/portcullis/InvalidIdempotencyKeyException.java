package io.github.sfesantos.portcullis;

public class InvalidIdempotencyKeyException extends PortcullisException {
    public InvalidIdempotencyKeyException(String message) {
        super(message, true);
    }

    @Override
    public int status() {
        return 400;
    }
}
