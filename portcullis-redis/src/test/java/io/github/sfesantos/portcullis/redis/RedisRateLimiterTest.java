package io.github.sfesantos.portcullis.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisRateLimiterTest {
    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static RedisClient lettuceClient;
    private static StatefulRedisConnection<String, String> lettuce;
    private static redis.clients.jedis.RedisClient jedis;

    private final Duration minute = Duration.ofMinutes(1);

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

    private RedisRateLimiter lettuceLimiter() {
        return RedisRateLimiter.of(new LettuceRedisScripts(lettuce.sync()));
    }

    private RedisRateLimiter jedisLimiter() {
        return RedisRateLimiter.of(new JedisRedisScripts(jedis));
    }

    @Test
    void allowsBurstUpToLimitThenDenies() {
        var limiter = lettuceLimiter();

        for (var i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", "alice", 3, minute).allowed()).isTrue();
        }

        var denied = limiter.tryAcquire("k", "alice", 3, minute);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isBetween(Duration.ofSeconds(19), Duration.ofSeconds(20));
    }

    @Test
    void instancesShareOneLimit() {
        var instanceA = lettuceLimiter();
        var instanceB = jedisLimiter();

        assertThat(instanceA.tryAcquire("k", "alice", 2, minute).allowed()).isTrue();
        assertThat(instanceB.tryAcquire("k", "alice", 2, minute).allowed()).isTrue();
        assertThat(instanceA.tryAcquire("k", "alice", 2, minute).allowed()).isFalse();
        assertThat(instanceB.tryAcquire("k", "alice", 2, minute).allowed()).isFalse();
    }

    @Test
    void subjectsAreIndependent() {
        var limiter = jedisLimiter();
        limiter.tryAcquire("k", "alice", 1, minute);
        assertThat(limiter.tryAcquire("k", "bob", 1, minute).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", "alice", 1, minute).allowed()).isFalse();
    }

    @Test
    void refillsOverTheWindow() throws InterruptedException {
        var limiter = lettuceLimiter();

        for (var i = 0; i < 10; i++) {
            limiter.tryAcquire("k", "alice", 10, Duration.ofSeconds(1));
        }

        assertThat(limiter.tryAcquire("k", "alice", 10, Duration.ofSeconds(1)).allowed()).isFalse();
        Thread.sleep(150);
        assertThat(limiter.tryAcquire("k", "alice", 10, Duration.ofSeconds(1)).allowed()).isTrue();
    }

    @Test
    void reloadsTheScriptAfterRedisLosesIt() {
        var limiter = jedisLimiter();
        limiter.tryAcquire("k", "alice", 5, minute);
        lettuce.sync().scriptFlush();
        assertThat(limiter.tryAcquire("k", "alice", 5, minute).allowed()).isTrue();
    }

    @Test
    void bucketsExpireWhenIdle() {
        lettuceLimiter().tryAcquire("k", "alice", 5, minute);
        var ttl = lettuce.sync().pttl("portcullis:rl:k:alice");
        assertThat(ttl).isBetween(1L, minute.toMillis());
    }

    @Test
    void failureDeniesByDefault() {
        var limiter = RedisRateLimiter.of(new BrokenRedis());
        assertThatThrownBy(() -> limiter.tryAcquire("k", "alice", 1, minute)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failureCanBeConfiguredToAllow() {
        var limiter = RedisRateLimiter.builder(new BrokenRedis()).onFailure(OnRedisFailure.ALLOW).build();
        assertThat(limiter.tryAcquire("k", "alice", 1, minute).allowed()).isTrue();
    }

    private static final class BrokenRedis implements RedisScripts {
        @Override
        public List<Object> evalSha(String sha1, String key, String... args) {
            throw new IllegalStateException("connection refused");
        }

        @Override
        public List<Object> eval(String script, String key, String... args) {
            throw new IllegalStateException("connection refused");
        }
    }
}
