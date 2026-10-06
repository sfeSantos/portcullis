package io.github.sfesantos.portcullis.policy;

import java.util.Set;

final class Authorities {

    private Authorities() {}

    static boolean holdsAny(Set<String> held, String[] wanted) {
        for (var value : wanted) {
            if (held.contains(value)) {
                return true;
            }
        }

        return false;
    }

    static boolean holdsAll(Set<String> held, String[] wanted) {
        for (var value : wanted) {
            if (!held.contains(value)) {
                return false;
            }
        }

        return true;
    }
}
