package io.github.sfesantos.portcullis.policy.coverage;

import io.github.sfesantos.portcullis.annotation.Authenticated;
import io.github.sfesantos.portcullis.annotation.OwnedBy;
import io.github.sfesantos.portcullis.annotation.PublicAccess;
import io.github.sfesantos.portcullis.annotation.RateLimit;
import io.github.sfesantos.portcullis.annotation.ResourceId;

public class MixedApi {

    @Authenticated
    public void authenticated() {}

    @OwnedBy(MixedApi.class)
    public void owned(@ResourceId Long id) {}

    @PublicAccess
    public void open() {}

    @PublicAccess
    @RateLimit(requests = 5)
    public void openAndLimited() {}

    @RateLimit(requests = 5)
    public void onlyLimited() {}

    public void forgotten() {}

    public static void utility() {}

    void packagePrivate() {}

    @Override
    public String toString() {
        return "MixedApi";
    }
}
