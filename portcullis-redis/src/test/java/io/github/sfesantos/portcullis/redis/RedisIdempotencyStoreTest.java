package io.github.sfesantos.portcullis.redis;

import io.github.sfesantos.portcullis.idempotency.Reservation;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisIdempotencyStoreTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final ResultCodec INTEGERS = new ResultCodec() {
        @Override
        public String encode(Object result) {
            return result.toString();
        }

        @Override
        public Object decode(String encoded, Type type) {
            return type == Integer.class ? Integer.valueOf(encoded) : encoded;
        }
    };

    private static RedisClient lettuceClient;
    private static StatefulRedisConnection<String, String> lettuce;
    private static redis.clients.jedis.RedisClient jedis;

    private final Duration lease = Duration.ofMinutes(1);
    private final Duration ttl = Duration.ofHours(1);

    @BeforeAll
    static void connect() {
        lettuceClient = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        lettuce = lettuceClient.connect();
        jedis = redis.clients.jedis.RedisClient.create(REDIS.getHost(), REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void disconnect() {
        lettuce.close();
        lettuceClient.shutdown();
        jedis.close();
    }

    @BeforeEach
    void flush() {
        lettuce.sync().flushall();
        lettuce.sync().scriptFlush();
    }

    private RedisIdempotencyStore lettuceStore() {
        return RedisIdempotencyStore.of(new LettuceRedisScripts(lettuce.sync()), INTEGERS);
    }

    private RedisIdempotencyStore jedisStore() {
        return RedisIdempotencyStore.of(new JedisRedisScripts(jedis), INTEGERS);
    }

    @Test
    void secondInstanceSeesTheFirstOneRunning() {
        assertThat(lettuceStore().reserve("k", lease, Integer.class)).isEqualTo(Reservation.ACQUIRED);
        assertThat(jedisStore().reserve("k", lease, Integer.class)).isEqualTo(Reservation.IN_PROGRESS);
    }

    @Test
    void resultStoredByOneInstanceIsReplayedByAnother() {
        lettuceStore().reserve("k", lease, Integer.class);
        lettuceStore().complete("k", 42, ttl);
        assertThat(jedisStore().reserve("k", lease, Integer.class)).isEqualTo(Reservation.completed(42));
        assertThat(lettuceStore().reserve("k", lease, Integer.class)).isEqualTo(Reservation.completed(42));
    }

    @Test
    void nullResultIsReplayed() {
        jedisStore().reserve("k", lease, Void.class);
        jedisStore().complete("k", null, ttl);
        assertThat(lettuceStore().reserve("k", lease, Void.class)).isEqualTo(Reservation.completed(null));
    }

    @Test
    void releaseFreesAnInProgressKeyButKeepsAResult() {
        var store = lettuceStore();
        store.reserve("k", lease, Integer.class);
        store.release("k");
        assertThat(store.reserve("k", lease, Integer.class)).isEqualTo(Reservation.ACQUIRED);

        store.complete("k", 7, ttl);
        store.release("k");
        assertThat(store.reserve("k", lease, Integer.class)).isEqualTo(Reservation.completed(7));
    }

    @Test
    void leaseAndTtlAreSetOnTheKey() {
        var store = lettuceStore();
        store.reserve("k", lease, Integer.class);
        assertThat(lettuce.sync().pttl("portcullis:idem:k")).isBetween(1L, lease.toMillis());
        store.complete("k", 1, ttl);
        assertThat(lettuce.sync().pttl("portcullis:idem:k")).isBetween(lease.toMillis(), ttl.toMillis());
    }
}
