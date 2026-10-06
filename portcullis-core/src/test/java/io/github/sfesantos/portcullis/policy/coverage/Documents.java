package io.github.sfesantos.portcullis.policy.coverage;

import io.github.sfesantos.portcullis.annotation.OwnedBy;
import io.github.sfesantos.portcullis.annotation.ResourceId;

public interface Documents {

    @OwnedBy(Documents.class)
    void read(@ResourceId Long id);
}
