package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.AnonymousKeyProvider;
import io.github.sfesantos.portcullis.RateLimitExceededException;
import io.github.sfesantos.portcullis.ratelimit.RateLimiter;

import java.time.Duration;

final class RateLimitRule implements AccessRule {

    static final String ANONYMOUS = "anonymous";

    private final String key;
    private final int requests;
    private final Duration window;
    private final RateLimiter limiter;
    private final AnonymousKeyProvider anonymousKeys;

    RateLimitRule(String key, int requests, Duration window, RateLimiter limiter, AnonymousKeyProvider anonymousKeys) {
        this.key = key;
        this.requests = requests;
        this.window = window;
        this.limiter = limiter;
        this.anonymousKeys = anonymousKeys;
    }

    @Override
    public void verify(AccessRequest request) {
        var subject = request.user() == null ? anonymousSubject() : request.user().id();
        var decision = limiter.tryAcquire(key, subject, requests, window);

        if (!decision.allowed()) {
            throw new RateLimitExceededException(request.operation() + ": rate limit exceeded", decision.retryAfter());
        }
    }

    // With no key, every anonymous caller shares one bucket: the stricter side.
    private String anonymousSubject() {
        var anonymousKey = anonymousKeys.anonymousKey();

        if (anonymousKey == null || anonymousKey.isEmpty() || anonymousKey.get().isEmpty()) {
            return ANONYMOUS;
        }

        return ANONYMOUS + ":" + anonymousKey.get();
    }
}
