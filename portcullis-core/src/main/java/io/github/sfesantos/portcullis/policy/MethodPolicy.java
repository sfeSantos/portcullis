package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.SecurityPrincipal;

public final class MethodPolicy {

    private static final AccessRule[] NO_RULES = new AccessRule[0];

    private final String operation;
    private final AccessRule[] rules;
    private final boolean needsArguments;

    MethodPolicy(String operation, AccessRule[] rules) {
        this.operation = operation;
        this.rules = rules;
        var anyNeedsArguments = false;

        for (var rule : rules) {
            anyNeedsArguments |= rule.needsArguments();
        }

        this.needsArguments = anyNeedsArguments;
    }

    static MethodPolicy unrestricted(String operation) {
        return new MethodPolicy(operation, NO_RULES);
    }

    public String operation() {
        return operation;
    }

    public boolean isUnrestricted() {
        return rules.length == 0;
    }

    public boolean needsArguments() {
        return needsArguments;
    }

    public void verify(SecurityPrincipal user, Object[] arguments) {
        var request = new AccessRequest(operation, user, arguments);

        for (var rule : rules) {
            rule.verify(request);
        }
    }
}
