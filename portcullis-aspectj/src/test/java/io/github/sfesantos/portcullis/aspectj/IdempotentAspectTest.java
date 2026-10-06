package io.github.sfesantos.portcullis.aspectj;

import io.github.sfesantos.portcullis.ForbiddenException;
import io.github.sfesantos.portcullis.Portcullis;
import io.github.sfesantos.portcullis.PortcullisContext;
import io.github.sfesantos.portcullis.SecurityPrincipal;
import io.github.sfesantos.portcullis.SimplePrincipal;
import io.github.sfesantos.portcullis.annotation.IdempotencyKey;
import io.github.sfesantos.portcullis.annotation.Idempotent;
import io.github.sfesantos.portcullis.annotation.OwnedBy;
import io.github.sfesantos.portcullis.annotation.ResourceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentAspectTest {

    record Account(long id) {
    }

    static class TransferController {

        int executions;

        @Idempotent
        @OwnedBy(Account.class)
        public String transfer(@ResourceId Long from, long amount, @IdempotencyKey String key) {
            executions++;

            return "transfer " + executions;
        }
    }

    private final TransferController controller = new TransferController();
    private final SimplePrincipal alice = SimplePrincipal.of("alice");

    @BeforeEach
    void configure() {
        Portcullis.configure()
                .ownership(Account.class, (SecurityPrincipal user, Long id) -> id == 1L)
                .install();
    }

    @AfterEach
    void reset() {
        Portcullis.reset();
        PortcullisContext.clear();
    }

    @Test
    void retriedRequestRunsOnceAndGetsTheSameAnswer() {
        var first = PortcullisContext.callAs(alice, () -> controller.transfer(1L, 500, "k1"));
        var retry = PortcullisContext.callAs(alice, () -> controller.transfer(1L, 500, "k1"));

        assertThat(first).isEqualTo("transfer 1");
        assertThat(retry).isEqualTo("transfer 1");
        assertThat(controller.executions).isEqualTo(1);
    }

    @Test
    void deniedCallDoesNotTakeTheKey() {
        assertThatThrownBy(() -> PortcullisContext.runAs(alice, () -> controller.transfer(2L, 500, "k1")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(PortcullisContext.callAs(alice, () -> controller.transfer(1L, 500, "k1"))).isEqualTo("transfer 1");
    }
}
