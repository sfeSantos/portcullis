package io.github.sfesantos.portcullis.redis;

import java.util.List;

public interface RedisScripts {

    // Must throw NoScriptException when Redis answers NOSCRIPT, so the script gets loaded with eval.
    List<Object> evalSha(String sha1, String key, String... args);
    List<Object> eval(String script, String key, String... args);
}
