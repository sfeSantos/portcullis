package io.github.sfesantos.portcullis;

public abstract class PortcullisException extends RuntimeException {
    protected PortcullisException(String message) {
        super(message);
    }

    protected PortcullisException(String message, Throwable cause) {
        super(message, cause);
    }

    // Denials skip the stack trace: under a flood of bad requests it is the most expensive part.
    protected PortcullisException(String message, boolean lightweight) {
        super(message, null, !lightweight, !lightweight);
    }

    public abstract int status();
}
