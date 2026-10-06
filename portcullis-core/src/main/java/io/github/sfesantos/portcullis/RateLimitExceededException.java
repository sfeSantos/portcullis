package io.github.sfesantos.portcullis;

import java.time.Duration;

public class RateLimitExceededException extends PortcullisException {

    private final Duration retryAfter;

    public RateLimitExceededException(String message, Duration retryAfter) {
        super(message, true);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    @Override
    public int status() {
        return 429;
    }
}
