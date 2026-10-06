package io.github.sfesantos.portcullis.redis;

import io.github.sfesantos.portcullis.ratelimit.RateLimiter;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;

public final class RedisRateLimiter implements RateLimiter {
    private static final System.Logger LOG = System.getLogger(RedisRateLimiter.class.getName());
    private static final RedisScript TOKEN_BUCKET = RedisScript.load("token-bucket.lua");
    private final RedisScripts redis;
    private final String prefix;
    private final OnRedisFailure onFailure;

    private RedisRateLimiter(Builder builder) {
        this.redis = builder.redis;
        this.prefix = builder.prefix;
        this.onFailure = builder.onFailure;
    }

    public static RedisRateLimiter of(RedisScripts redis) {
        return builder(redis).build();
    }

    public static Builder builder(RedisScripts redis) {
        return new Builder(redis);
    }

    @Override
    public Decision tryAcquire(String key, String subject, int limit, Duration window) {
        var bucket = prefix + key + ':' + subject;
        var windowMicros = Long.toString(window.toNanos() / 1_000);

        try {
            var result = TOKEN_BUCKET.run(redis, bucket, Integer.toString(limit), windowMicros);
            var allowed = ((Number) result.get(0)).longValue() == 1L;

            return allowed ? Decision.allow() : Decision.deny(Duration.ofNanos(((Number) result.get(1)).longValue() * 1_000));
        } catch (RuntimeException e) {
            if (onFailure == OnRedisFailure.DENY) {
                throw e;
            }

            LOG.log(Level.WARNING, "Redis unavailable, letting the call through", e);

            return Decision.allow();
        }
    }

    public static final class Builder {
        private final RedisScripts redis;
        private String prefix = "portcullis:rl:";
        private OnRedisFailure onFailure = OnRedisFailure.DENY;

        private Builder(RedisScripts redis) {
            this.redis = Objects.requireNonNull(redis, "redis");
        }

        public Builder keyPrefix(String prefix) {
            this.prefix = Objects.requireNonNull(prefix, "prefix");

            return this;
        }

        public Builder onFailure(OnRedisFailure onFailure) {
            this.onFailure = Objects.requireNonNull(onFailure, "onFailure");

            return this;
        }

        public RedisRateLimiter build() {
            return new RedisRateLimiter(this);
        }
    }
}
