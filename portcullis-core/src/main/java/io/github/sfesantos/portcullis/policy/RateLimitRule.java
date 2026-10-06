package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.RateLimitExceededException;
import io.github.sfesantos.portcullis.ratelimit.RateLimiter;

import java.time.Duration;

final class RateLimitRule implements AccessRule {

    static final String ANONYMOUS = "anonymous";

    private final String key;
    private final int requests;
    private final Duration window;
    private final RateLimiter limiter;

    RateLimitRule(String key, int requests, Duration window, RateLimiter limiter) {
        this.key = key;
        this.requests = requests;
        this.window = window;
        this.limiter = limiter;
    }

    @Override
    public void verify(AccessRequest request) {
        var subject = request.user() == null ? ANONYMOUS : request.user().id();
        var decision = limiter.tryAcquire(key, subject, requests, window);

        if (!decision.allowed()) {
            throw new RateLimitExceededException(request.operation() + ": rate limit exceeded", decision.retryAfter());
        }
    }
}
