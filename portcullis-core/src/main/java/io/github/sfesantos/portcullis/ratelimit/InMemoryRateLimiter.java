package io.github.sfesantos.portcullis.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class InMemoryRateLimiter implements RateLimiter {

    private static final long SWEEP_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, TokenBucket>> limits = new ConcurrentHashMap<>();
    private final AtomicLong nextSweep;
    private final LongSupplier nanoClock;

    public InMemoryRateLimiter() {
        this(System::nanoTime);
    }

    public InMemoryRateLimiter(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.nextSweep = new AtomicLong(nanoClock.getAsLong() + SWEEP_INTERVAL_NANOS);
    }

    @Override
    public Decision tryAcquire(String key, String subject, int limit, Duration window) {
        var now = nanoClock.getAsLong();
        sweepNowAndThen(now);

        return bucket(key, subject, limit, window, now).take(now);
    }

    public int size() {
        return limits.values().stream().mapToInt(ConcurrentHashMap::size).sum();
    }

    private TokenBucket bucket(String key, String subject, int limit, Duration window, long now) {
        var subjects = limits.get(key);

        if (subjects == null) {
            subjects = limits.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        }

        var bucket = subjects.get(subject);

        if (bucket == null) {
            bucket = subjects.computeIfAbsent(subject, s -> new TokenBucket(limit, window, now));
        }

        return bucket;
    }

    private void sweepNowAndThen(long now) {
        var due = nextSweep.get();

        if (now - due >= 0 && nextSweep.compareAndSet(due, now + SWEEP_INTERVAL_NANOS)) {
            limits.values().forEach(subjects -> subjects.values().removeIf(b -> b.isIdle(now)));
        }
    }
}
