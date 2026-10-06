package io.github.sfesantos.portcullis.idempotency;

@FunctionalInterface
public interface Invocation {

    Object proceed() throws Throwable;
}
