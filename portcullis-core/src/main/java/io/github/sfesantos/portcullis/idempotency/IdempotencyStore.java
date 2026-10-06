package io.github.sfesantos.portcullis.idempotency;

import java.lang.reflect.Type;
import java.time.Duration;

public interface IdempotencyStore {
    // Atomic: only one caller may get ACQUIRED for a key until it is completed, released or the lease ends.
    Reservation reserve(String key, Duration lease, Type resultType);

    void complete(String key, Object result, Duration ttl);

    void release(String key);
}
