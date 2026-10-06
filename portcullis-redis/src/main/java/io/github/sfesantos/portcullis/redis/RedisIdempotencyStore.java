package io.github.sfesantos.portcullis.redis;

import io.github.sfesantos.portcullis.idempotency.IdempotencyStore;
import io.github.sfesantos.portcullis.idempotency.Reservation;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;

public final class RedisIdempotencyStore implements IdempotencyStore {
    private static final RedisScript RESERVE = RedisScript.load("idempotency-reserve.lua");
    private static final RedisScript COMPLETE = RedisScript.load("idempotency-complete.lua");
    private static final RedisScript RELEASE = RedisScript.load("idempotency-release.lua");

    private static final String IN_PROGRESS = "p";
    private static final String COMPLETED_NULL = "n";
    private static final char COMPLETED = 'c';

    private final RedisScripts redis;
    private final ResultCodec codec;
    private final String prefix;

    private RedisIdempotencyStore(RedisScripts redis, ResultCodec codec, String prefix) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    public static RedisIdempotencyStore of(RedisScripts redis, ResultCodec codec) {
        return new RedisIdempotencyStore(redis, codec, "portcullis:idem:");
    }

    public RedisIdempotencyStore withKeyPrefix(String prefix) {
        return new RedisIdempotencyStore(redis, codec, prefix);
    }

    @Override
    public Reservation reserve(String key, Duration lease, Type resultType) {
        var reply = RESERVE.run(redis, prefix + key, Long.toString(lease.toMillis()));

        if (((Number) reply.get(0)).longValue() == 1L) {
            return Reservation.ACQUIRED;
        }

        var stored = (String) reply.get(1);

        if (stored.equals(IN_PROGRESS)) {
            return Reservation.IN_PROGRESS;
        }

        if (stored.equals(COMPLETED_NULL)) {
            return Reservation.completed(null);
        }

        return Reservation.completed(codec.decode(stored.substring(1), resultType));
    }

    @Override
    public void complete(String key, Object result, Duration ttl) {
        var value = result == null ? COMPLETED_NULL : COMPLETED + codec.encode(result);
        COMPLETE.run(redis, prefix + key, value, Long.toString(ttl.toMillis()));
    }

    @Override
    public void release(String key) {
        RELEASE.run(redis, prefix + key);
    }
}
