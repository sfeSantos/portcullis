package io.github.sfesantos.portcullis.audit;

public enum Check {

    AUTHENTICATION,
    RATE_LIMIT,
    ROLE,
    PERMISSION,
    TENANT,
    OWNERSHIP,

    POLICY,

    ERROR
}
