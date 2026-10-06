package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.AnonymousKeyProvider;
import io.github.sfesantos.portcullis.OwnershipResolver;
import io.github.sfesantos.portcullis.TenantResolver;
import io.github.sfesantos.portcullis.ratelimit.RateLimiter;

import java.util.Map;
import java.util.Objects;

public record Resolvers(Map<Class<?>, OwnershipResolver<?>> ownership,
                        Map<Class<?>, TenantResolver<?>> tenancy,
                        RateLimiter rateLimiter,
                        AnonymousKeyProvider anonymousKeys) {

    public Resolvers {
        ownership = Map.copyOf(ownership);
        tenancy = Map.copyOf(tenancy);
        Objects.requireNonNull(anonymousKeys, "anonymousKeys");
    }
}
