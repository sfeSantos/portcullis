package io.github.sfesantos.portcullis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortcullisContextTest {

    @AfterEach
    void clear() {
        PortcullisContext.clear();
    }

    @Test
    void bindRestoresPreviousUserOnClose() {
        var outer = SimplePrincipal.of("outer");
        var inner = SimplePrincipal.of("inner");

        try (var ignored = PortcullisContext.bind(outer)) {
            PortcullisContext.runAs(inner, () -> assertThat(PortcullisContext.current()).contains(inner));
            assertThat(PortcullisContext.current()).contains(outer);
        }

        assertThat(PortcullisContext.current()).isEmpty();
    }

    @Test
    void defaultGuardReadsTheContext() {
        Portcullis.reset();
        var user = SimplePrincipal.of("42");
        assertThat(PortcullisContext.callAs(user, Portcullis::requirePrincipal)).isEqualTo(user);
    }
}
