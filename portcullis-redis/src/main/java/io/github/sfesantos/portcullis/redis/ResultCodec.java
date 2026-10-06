package io.github.sfesantos.portcullis.redis;

import java.lang.reflect.Type;

public interface ResultCodec {
    String encode(Object result);

    Object decode(String encoded, Type type);
}
