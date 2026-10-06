package io.github.sfesantos.portcullis.idempotency;

import java.lang.reflect.Type;
import java.time.Duration;

record IdempotencyPolicy(String operation, Duration ttl, boolean required, int keyIndex, Type resultType) {

    static final int NO_KEY_PARAMETER = -1;

    String keyFrom(Object[] arguments) {
        if (keyIndex == NO_KEY_PARAMETER) {
            return null;
        }

        var value = arguments[keyIndex];

        return value == null ? null : value.toString();
    }
}
