package io.github.sfesantos.portcullis.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.sfesantos.portcullis.idempotency.IdempotencyStore;
import io.github.sfesantos.portcullis.idempotency.Reservation;

import java.lang.reflect.Type;
import java.time.Duration;

public final class CaffeineIdempotencyStore implements IdempotencyStore {
    public static final long DEFAULT_MAXIMUM_SIZE = 100_000;

    private final Cache<String, Entry> entries;

    private CaffeineIdempotencyStore(long maximumSize, Ticker ticker) {
        this.entries = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfter(new EntryExpiry())
                .ticker(ticker)
                .build();
    }

    public static CaffeineIdempotencyStore create() {
        return new CaffeineIdempotencyStore(DEFAULT_MAXIMUM_SIZE, Ticker.systemTicker());
    }

    public static CaffeineIdempotencyStore withMaximumSize(long maximumSize) {
        return new CaffeineIdempotencyStore(maximumSize, Ticker.systemTicker());
    }

    static CaffeineIdempotencyStore withTicker(long maximumSize, Ticker ticker) {
        return new CaffeineIdempotencyStore(maximumSize, ticker);
    }

    @Override
    public Reservation reserve(String key, Duration lease, Type resultType) {
        var reservation = new Reservation[1];
        entries.asMap().compute(key, (k, current) -> {
            if (current == null) {
                reservation[0] = Reservation.ACQUIRED;

                return Entry.inProgress(lease);
            }

            reservation[0] = current.completed() ? Reservation.completed(current.result()) : Reservation.IN_PROGRESS;

            return current;
        });

        return reservation[0];
    }

    @Override
    public void complete(String key, Object result, Duration ttl) {
        entries.put(key, Entry.completed(result, ttl));
    }

    @Override
    public void release(String key) {
        entries.asMap().computeIfPresent(key, (k, current) -> current.completed() ? current : null);
    }

    long size() {
        entries.cleanUp();

        return entries.estimatedSize();
    }

    private record Entry(boolean completed, Object result, long lifetimeNanos) {
        static Entry inProgress(Duration lease) {
            return new Entry(false, null, lease.toNanos());
        }

        static Entry completed(Object result, Duration ttl) {
            return new Entry(true, result, ttl.toNanos());
        }
    }

    private static final class EntryExpiry implements Expiry<String, Entry> {
        @Override
        public long expireAfterCreate(String key, Entry entry, long currentTime) {
            return entry.lifetimeNanos();
        }

        @Override
        public long expireAfterUpdate(String key, Entry entry, long currentTime, long currentDuration) {
            return entry.lifetimeNanos();
        }

        @Override
        public long expireAfterRead(String key, Entry entry, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
