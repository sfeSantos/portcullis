package io.github.sfesantos.portcullis.caffeine;

import io.github.sfesantos.portcullis.idempotency.Reservation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineIdempotencyStoreTest {

    private final AtomicLong now = new AtomicLong();
    private final CaffeineIdempotencyStore store = CaffeineIdempotencyStore.withTicker(1_000, now::get);
    private final Duration lease = Duration.ofMinutes(1);
    private final Duration ttl = Duration.ofHours(1);

    @Test
    void firstReserveAcquiresAndSecondSeesInProgress() {
        assertThat(store.reserve("k", lease, String.class)).isEqualTo(Reservation.ACQUIRED);
        assertThat(store.reserve("k", lease, String.class)).isEqualTo(Reservation.IN_PROGRESS);
    }

    @Test
    void completedResultIsReplayed() {
        store.reserve("k", lease, String.class);
        store.complete("k", "done", ttl);
        assertThat(store.reserve("k", lease, String.class)).isEqualTo(Reservation.completed("done"));
    }

    @Test
    void releaseFreesAnInProgressKeyButKeepsAResult() {
        store.reserve("k", lease, String.class);
        store.release("k");
        assertThat(store.reserve("k", lease, String.class)).isEqualTo(Reservation.ACQUIRED);

        store.complete("k", "done", ttl);
        store.release("k");
        assertThat(store.reserve("k", lease, String.class)).isEqualTo(Reservation.completed("done"));
    }

    @Test
    void leaseAndTtlExpire() {
        store.reserve("lease", lease, String.class);
        store.reserve("result", lease, String.class);
        store.complete("result", "done", ttl);

        now.addAndGet(Duration.ofMinutes(2).toNanos());
        assertThat(store.reserve("lease", lease, String.class)).isEqualTo(Reservation.ACQUIRED);
        assertThat(store.reserve("result", lease, String.class)).isEqualTo(Reservation.completed("done"));

        now.addAndGet(Duration.ofHours(2).toNanos());
        assertThat(store.reserve("result", lease, String.class)).isEqualTo(Reservation.ACQUIRED);
    }
}
