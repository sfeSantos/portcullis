package io.github.sfesantos.portcullis.ratelimit;

import java.time.Duration;

@FunctionalInterface
public interface RateLimiter {
    Decision tryAcquire(String key, String subject, int limit, Duration window);

    record Decision(boolean allowed, Duration retryAfter) {
        private static final Decision ALLOWED = new Decision(true, Duration.ZERO);

        public static Decision allow() {
            return ALLOWED;
        }

        public static Decision deny(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }
}
