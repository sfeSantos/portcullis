package io.github.sfesantos.portcullis.caffeine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineRateLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final CaffeineRateLimiter limiter = CaffeineRateLimiter.withTicker(1_000, now::get);
    private final Duration minute = Duration.ofMinutes(1);

    @Test
    void allowsBurstUpToLimitThenDenies() {
        for (var i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", "alice", 3, minute).allowed()).isTrue();
        }

        var denied = limiter.tryAcquire("k", "alice", 3, minute);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void refillsOverTheWindow() {
        for (var i = 0; i < 3; i++) {
            limiter.tryAcquire("k", "alice", 3, minute);
        }

        now.addAndGet(Duration.ofSeconds(20).toNanos());
        assertThat(limiter.tryAcquire("k", "alice", 3, minute).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", "alice", 3, minute).allowed()).isFalse();
    }

    @Test
    void subjectsAndKeysAreIndependent() {
        limiter.tryAcquire("k", "alice", 1, minute);
        assertThat(limiter.tryAcquire("k", "bob", 1, minute).allowed()).isTrue();
        assertThat(limiter.tryAcquire("other", "alice", 1, minute).allowed()).isTrue();
    }

    @Test
    void idleBucketsExpireAfterTheirWindow() {
        limiter.tryAcquire("k", "alice", 1, Duration.ofSeconds(1));
        assertThat(limiter.size()).isEqualTo(1);
        now.addAndGet(Duration.ofSeconds(2).toNanos());
        assertThat(limiter.size()).isZero();
    }

    @Test
    void memoryStaysBounded() {
        var small = CaffeineRateLimiter.withTicker(10, now::get);

        for (var i = 0; i < 1_000; i++) {
            small.tryAcquire("k", "user-" + i, 1, minute);
        }

        assertThat(small.size()).isLessThanOrEqualTo(10);
    }
}
