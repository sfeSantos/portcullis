package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.HasResourceId;

record ResourceArgument(Class<?> resourceType, int index) {
    Object read(Object[] arguments) {
        var value = arguments[index];

        return value instanceof HasResourceId<?> holder ? holder.resourceId() : value;
    }

    String name() {
        return resourceType.getSimpleName();
    }
}
