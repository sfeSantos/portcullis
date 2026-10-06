package io.github.sfesantos.portcullis;

@FunctionalInterface
public interface HasResourceId<ID> {
    ID resourceId();
}
