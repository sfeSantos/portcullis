package io.github.sfesantos.portcullis.redis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

final class RedisScript {
    private final String source;
    private final String sha1;

    private RedisScript(String source) {
        this.source = source;
        this.sha1 = sha1(source);
    }

    static RedisScript load(String resource) {
        try (var in = RedisScript.class.getResourceAsStream(resource)) {
            return new RedisScript(new String(Objects.requireNonNull(in, resource).readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    List<Object> run(RedisScripts redis, String key, String... args) {
        try {
            return redis.evalSha(sha1, key, args);
        } catch (NoScriptException e) {
            return redis.eval(source, key, args);
        }
    }

    private static String sha1(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
