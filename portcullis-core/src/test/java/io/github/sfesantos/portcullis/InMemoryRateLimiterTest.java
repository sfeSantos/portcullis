package io.github.sfesantos.portcullis;

import io.github.sfesantos.portcullis.ratelimit.InMemoryRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(now::get);
    private final Duration minute = Duration.ofMinutes(1);

    @Test
    void allowsBurstUpToLimitThenDenies() {
        assertThat(limiter.tryAcquire("k", "u", 3, minute).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", "u", 3, minute).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", "u", 3, minute).allowed()).isTrue();

        var denied = limiter.tryAcquire("k", "u", 3, minute);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void refillsOverTheWindow() {
        for (var i = 0; i < 3; i++) {
            limiter.tryAcquire("k", "u", 3, minute);
        }

        now.addAndGet(Duration.ofSeconds(20).toNanos());
        assertThat(limiter.tryAcquire("k", "u", 3, minute).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", "u", 3, minute).allowed()).isFalse();
    }

    @Test
    void subjectsAndKeysAreIndependent() {
        limiter.tryAcquire("k", "u", 1, minute);
        assertThat(limiter.tryAcquire("k", "other", 1, minute).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k2", "u", 1, minute).allowed()).isTrue();
    }

    @Test
    void idleBucketsAreSwept() {
        limiter.tryAcquire("k", "u", 1, Duration.ofSeconds(1));
        assertThat(limiter.size()).isEqualTo(1);
        now.addAndGet(Duration.ofMinutes(2).toNanos());
        limiter.tryAcquire("k", "v", 1, Duration.ofSeconds(1));
        assertThat(limiter.size()).isEqualTo(1);
    }
}
