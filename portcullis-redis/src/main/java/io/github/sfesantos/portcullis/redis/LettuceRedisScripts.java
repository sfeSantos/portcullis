package io.github.sfesantos.portcullis.redis;

import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisScriptingCommands;

import java.util.List;

public final class LettuceRedisScripts implements RedisScripts {
    private final RedisScriptingCommands<String, String> commands;

    public LettuceRedisScripts(RedisScriptingCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    public List<Object> evalSha(String sha1, String key, String... args) {
        try {
            return commands.evalsha(sha1, ScriptOutputType.MULTI, new String[] {key}, args);
        } catch (RedisNoScriptException e) {
            throw new NoScriptException(e);
        }
    }

    @Override
    public List<Object> eval(String script, String key, String... args) {
        return commands.eval(script, ScriptOutputType.MULTI, new String[] {key}, args);
    }
}
