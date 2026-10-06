package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.ForbiddenException;
import io.github.sfesantos.portcullis.SecurityPrincipal;
import io.github.sfesantos.portcullis.annotation.Match;
import io.github.sfesantos.portcullis.audit.Check;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

final class AuthorityRule implements AccessRule {
    private final Check check;
    private final Function<SecurityPrincipal, Set<String>> authorities;
    private final String[] required;
    private final Match match;
    private final String description;

    private AuthorityRule(Check check, Function<SecurityPrincipal, Set<String>> authorities, String[] required, Match match) {
        this.check = check;
        this.authorities = authorities;
        this.required = required.clone();
        this.match = match;
        this.description = (check == Check.ROLE ? "role" : "permission") + " " + match + Arrays.toString(required);
    }

    static AuthorityRule roles(String[] required, Match match) {
        return new AuthorityRule(Check.ROLE, SecurityPrincipal::roles, required, match);
    }

    static AuthorityRule permissions(String[] required, Match match) {
        return new AuthorityRule(Check.PERMISSION, SecurityPrincipal::permissions, required, match);
    }

    @Override
    public void verify(AccessRequest request) {
        var held = authorities.apply(request.user());
        var granted = match == Match.ALL ? Authorities.holdsAll(held, required) : Authorities.holdsAny(held, required);

        if (!granted) {
            throw new ForbiddenException(check, request.operation() + ": requires " + description);
        }
    }
}
