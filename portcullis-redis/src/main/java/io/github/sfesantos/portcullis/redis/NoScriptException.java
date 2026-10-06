package io.github.sfesantos.portcullis.redis;

public class NoScriptException extends RuntimeException {
    public NoScriptException(Throwable cause) {
        super("script not loaded in Redis", cause);
    }
}
