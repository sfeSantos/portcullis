package io.github.sfesantos.portcullis.policy;

interface AccessRule {
    void verify(AccessRequest request);

    default boolean needsArguments() {
        return false;
    }
}
