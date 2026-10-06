package io.github.sfesantos.portcullis.aspectj;

import io.github.sfesantos.portcullis.ForbiddenException;
import io.github.sfesantos.portcullis.Portcullis;
import io.github.sfesantos.portcullis.PortcullisContext;
import io.github.sfesantos.portcullis.SecurityPrincipal;
import io.github.sfesantos.portcullis.SimplePrincipal;
import io.github.sfesantos.portcullis.UnauthenticatedException;
import io.github.sfesantos.portcullis.annotation.Authenticated;
import io.github.sfesantos.portcullis.annotation.OwnedBy;
import io.github.sfesantos.portcullis.annotation.PublicAccess;
import io.github.sfesantos.portcullis.annotation.RequiresRole;
import io.github.sfesantos.portcullis.annotation.ResourceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** These classes are woven by ajc at test-compile, exactly as an application would be. */
class PortcullisAspectTest {

    record Order(long id) {
    }

    static class OrderController {

        @OwnedBy(Order.class)
        public String get(@ResourceId Long id) {
            return "order " + id;
        }

        @RequiresRole("ADMIN")
        public String purge() {
            return "purged";
        }

        public String open() {
            return "open";
        }
    }

    @Authenticated
    static class ProfileController {

        public String me() {
            return PortcullisContext.current().map(SecurityPrincipal::id).orElseThrow();
        }

        @PublicAccess
        public String health() {
            return "ok";
        }

        public String viaHelper() {
            Runnable r = () -> helper();
            r.run();

            return "helper ran";
        }

        private void helper() {
        }

        public static String version() {
            return "1.0";
        }
    }

    private final OrderController orders = new OrderController();
    private final ProfileController profile = new ProfileController();
    private final SimplePrincipal alice = SimplePrincipal.of("alice");

    @BeforeEach
    void configure() {
        var owners = Map.of(1L, "alice", 2L, "bob");

        Portcullis.configure()
                .ownership(Order.class, (SecurityPrincipal user, Long id) -> user.id().equals(owners.get(id)))
                .install();
    }

    @AfterEach
    void reset() {
        Portcullis.reset();
        PortcullisContext.clear();
    }

    @Test
    void ownerGetsTheResource() {
        assertThat(PortcullisContext.callAs(alice, () -> orders.get(1L))).isEqualTo("order 1");
    }

    @Test
    void otherUsersResourceIsBlockedBeforeTheMethodRuns() {
        assertThatThrownBy(() -> PortcullisContext.runAs(alice, () -> orders.get(2L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anonymousCallIsBlocked() {
        assertThatThrownBy(() -> orders.get(1L)).isInstanceOf(UnauthenticatedException.class);
        assertThatThrownBy(profile::me).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void roleIsEnforced() {
        assertThatThrownBy(() -> PortcullisContext.runAs(alice, orders::purge)).isInstanceOf(ForbiddenException.class);
        var admin = SimplePrincipal.of("root").withRoles("ADMIN");
        assertThat(PortcullisContext.callAs(admin, orders::purge)).isEqualTo("purged");
    }

    @Test
    void unannotatedAndOptedOutMethodsStayOpen() {
        assertThat(orders.open()).isEqualTo("open");
        assertThat(profile.health()).isEqualTo("ok");
        assertThat(ProfileController.version()).isEqualTo("1.0");
    }

    @Test
    void classLevelAnnotationCoversPublicMethodsButNotTheirPrivateHelpers() {
        assertThat(PortcullisContext.callAs(alice, profile::me)).isEqualTo("alice");
        assertThat(PortcullisContext.callAs(alice, profile::viaHelper)).isEqualTo("helper ran");
    }
}
