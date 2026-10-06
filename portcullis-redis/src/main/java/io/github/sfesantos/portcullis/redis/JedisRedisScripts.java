package io.github.sfesantos.portcullis.redis;

import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisNoScriptException;

import java.util.List;

public final class JedisRedisScripts implements RedisScripts {

    private final UnifiedJedis jedis;

    public JedisRedisScripts(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object> evalSha(String sha1, String key, String... args) {
        try {
            return (List<Object>) jedis.evalsha(sha1, List.of(key), List.of(args));
        } catch (JedisNoScriptException e) {
            throw new NoScriptException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object> eval(String script, String key, String... args) {
        return (List<Object>) jedis.eval(script, List.of(key), List.of(args));
    }
}
