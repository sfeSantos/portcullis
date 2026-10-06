package io.github.sfesantos.portcullis.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.sfesantos.portcullis.ratelimit.RateLimiter;
import io.github.sfesantos.portcullis.ratelimit.TokenBucket;

import java.time.Duration;

public final class CaffeineRateLimiter implements RateLimiter {
    public static final long DEFAULT_MAXIMUM_SIZE = 100_000;

    private final Cache<BucketKey, TokenBucket> buckets;
    private final Ticker ticker;

    private CaffeineRateLimiter(long maximumSize, Ticker ticker) {
        this.ticker = ticker;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfter(Expiry.accessing((BucketKey key, TokenBucket bucket) -> Duration.ofNanos(bucket.windowNanos())))
                .ticker(ticker)
                .build();
    }

    public static CaffeineRateLimiter create() {
        return new CaffeineRateLimiter(DEFAULT_MAXIMUM_SIZE, Ticker.systemTicker());
    }

    // Bounds memory when many distinct callers show up; an evicted caller simply starts with a full bucket.
    public static CaffeineRateLimiter withMaximumSize(long maximumSize) {
        return new CaffeineRateLimiter(maximumSize, Ticker.systemTicker());
    }

    static CaffeineRateLimiter withTicker(long maximumSize, Ticker ticker) {
        return new CaffeineRateLimiter(maximumSize, ticker);
    }

    @Override
    public Decision tryAcquire(String key, String subject, int limit, Duration window) {
        var now = ticker.read();
        var bucket = buckets.get(new BucketKey(key, subject), k -> new TokenBucket(limit, window, now));

        return bucket.take(now);
    }

    long size() {
        buckets.cleanUp();

        return buckets.estimatedSize();
    }

    private record BucketKey(String key, String subject) {
    }
}
