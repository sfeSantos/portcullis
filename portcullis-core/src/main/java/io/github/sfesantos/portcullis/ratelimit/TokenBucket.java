package io.github.sfesantos.portcullis.ratelimit;

import io.github.sfesantos.portcullis.ratelimit.RateLimiter.Decision;

import java.time.Duration;

public final class TokenBucket {
    
    private final double capacity;
    private final double nanosPerToken;
    private final long windowNanos;
    private double tokens;
    private long lastRefill;

    public TokenBucket(int limit, Duration window, long nowNanos) {
        this.capacity = limit;
        this.windowNanos = window.toNanos();
        this.nanosPerToken = (double) windowNanos / limit;
        this.tokens = limit;
        this.lastRefill = nowNanos;
    }

    public synchronized Decision take(long nowNanos) {
        refill(nowNanos);

        if (tokens >= 1) {
            tokens -= 1;

            return Decision.allow();
        }

        var waitNanos = (long) Math.ceil((1 - tokens) * nanosPerToken);

        return Decision.deny(Duration.ofNanos(waitNanos));
    }

    // Idle for a whole window means full again, so the bucket can be dropped without changing anything.
    public synchronized boolean isIdle(long nowNanos) {
        return nowNanos - lastRefill >= windowNanos;
    }

    public long windowNanos() {
        return windowNanos;
    }

    private void refill(long nowNanos) {
        var elapsed = nowNanos - lastRefill;

        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed / nanosPerToken);
            lastRefill = nowNanos;
        }
    }
}
