package io.github.sfesantos.portcullis.idempotency;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final long SWEEP_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final LongSupplier nanoClock;
    private final AtomicLong nextSweep;

    public InMemoryIdempotencyStore() {
        this(System::nanoTime);
    }

    public InMemoryIdempotencyStore(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.nextSweep = new AtomicLong(nanoClock.getAsLong() + SWEEP_INTERVAL_NANOS);
    }

    @Override
    public Reservation reserve(String key, Duration lease, Type resultType) {
        var now = nanoClock.getAsLong();
        sweepNowAndThen(now);

        var reservation = new Reservation[1];
        entries.compute(key, (k, current) -> {
            if (current == null || current.isExpired(now)) {
                reservation[0] = Reservation.ACQUIRED;

                return Entry.inProgress(now + lease.toNanos());
            }

            reservation[0] = current.completed ? Reservation.completed(current.result) : Reservation.IN_PROGRESS;

            return current;
        });

        return reservation[0];
    }

    @Override
    public void complete(String key, Object result, Duration ttl) {
        entries.put(key, Entry.completed(result, nanoClock.getAsLong() + ttl.toNanos()));
    }

    @Override
    public void release(String key) {
        entries.computeIfPresent(key, (k, current) -> current.completed ? current : null);
    }

    public int size() {
        return entries.size();
    }

    private void sweepNowAndThen(long now) {
        var due = nextSweep.get();

        if (now - due >= 0 && nextSweep.compareAndSet(due, now + SWEEP_INTERVAL_NANOS)) {
            entries.values()
                    .removeIf(entry -> entry.isExpired(now));
        }
    }

    private record Entry(boolean completed, Object result, long expiresAt) {

        static Entry inProgress(long expiresAt) {
            return new Entry(false, null, expiresAt);
        }

        static Entry completed(Object result, long expiresAt) {
            return new Entry(true, result, expiresAt);
        }

        boolean isExpired(long now) {
            return now - expiresAt >= 0;
        }
    }
}
