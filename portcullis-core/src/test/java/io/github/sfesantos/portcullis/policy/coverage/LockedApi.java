package io.github.sfesantos.portcullis.policy.coverage;

import io.github.sfesantos.portcullis.annotation.RequiresRole;

@RequiresRole("ADMIN")
public class LockedApi {

    public void list() {}

    public void delete(Long id) {}
}
