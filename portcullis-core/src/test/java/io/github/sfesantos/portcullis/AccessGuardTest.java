package io.github.sfesantos.portcullis;

import io.github.sfesantos.portcullis.Fixtures.AccountApi;
import io.github.sfesantos.portcullis.Fixtures.DocumentService;
import io.github.sfesantos.portcullis.Fixtures.Documents;
import io.github.sfesantos.portcullis.Fixtures.Invoice;
import io.github.sfesantos.portcullis.Fixtures.Order;
import io.github.sfesantos.portcullis.Fixtures.OrderApi;
import io.github.sfesantos.portcullis.Fixtures.UpdateOrder;
import io.github.sfesantos.portcullis.audit.AccessEvent;
import io.github.sfesantos.portcullis.audit.Check;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessGuardTest {

    private static final Map<Long, String> ORDER_OWNERS = Map.of(1L, "alice", 2L, "bob");
    private static final Map<String, String> INVOICE_TENANTS = Map.of("inv-1", "acme", "inv-2", "globex");

    private static final SecurityPrincipal ALICE = SimplePrincipal.of("alice").withRoles("USER");
    private static final SecurityPrincipal ADMIN = SimplePrincipal.of("root").withRoles("ADMIN", "USER");

    private final AtomicReference<SecurityPrincipal> current = new AtomicReference<>();
    private final List<AccessEvent> events = new ArrayList<>();

    private final AccessGuard guard = AccessGuard.builder()
            .principalProvider(() -> java.util.Optional.ofNullable(current.get()))
            .ownership(Order.class, (SecurityPrincipal user, Long id) -> user.id().equals(ORDER_OWNERS.get(id)))
            .ownership(Fixtures.Item.class, (SecurityPrincipal user, Long id) -> id < 100)
            .tenancy(Invoice.class, (String tenant, String id) -> tenant.equals(INVOICE_TENANTS.get(id)))
            .auditListener(events::add)
            .build();

    private void as(SecurityPrincipal user) {
        current.set(user);
    }

    private void call(Class<?> type, String name, Object... args) {
        guard.check(method(type, name), type, args);
    }

    private static Method method(Class<?> type, String name) {
        for (var m : type.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }

        throw new IllegalArgumentException(name);
    }

    @Nested
    class Authentication {

        @Test
        void unannotatedMethodIsOpenAndNotAudited() {
            assertThatCode(() -> call(OrderApi.class, "open")).doesNotThrowAnyException();
            assertThat(events).isEmpty();
        }

        @Test
        void anonymousIsRejectedWith401() {
            assertThatThrownBy(() -> call(OrderApi.class, "me"))
                    .isInstanceOf(UnauthenticatedException.class)
                    .satisfies(e -> assertThat(((PortcullisException) e).status()).isEqualTo(401));
        }

        @Test
        void connectedUserPasses() {
            as(ALICE);
            assertThatCode(() -> call(OrderApi.class, "me")).doesNotThrowAnyException();
        }

        @Test
        void providerReturningNullCountsAsAnonymous() {
            var sloppy = AccessGuard.builder()
                    .principalProvider(() -> null)
                    .build();

            assertThat(sloppy.currentPrincipal()).isEmpty();
            assertThatThrownBy(() -> sloppy.check(method(OrderApi.class, "me"), OrderApi.class, new Object[0]))
                    .isInstanceOf(UnauthenticatedException.class);
        }

        @Test
        void denialsCarryNoStackTrace() {
            assertThatThrownBy(() -> call(OrderApi.class, "me"))
                    .satisfies(e -> assertThat(e.getStackTrace()).isEmpty());
        }
    }

    @Nested
    class Roles {

        @Test
        void anyOfTheRolesIsEnough() {
            as(SimplePrincipal.of("s").withRoles("SUPPORT"));
            assertThatCode(() -> call(OrderApi.class, "anyRole")).doesNotThrowAnyException();
        }

        @Test
        void missingRoleIs403() {
            as(ALICE);
            assertThatThrownBy(() -> call(OrderApi.class, "anyRole"))
                    .isInstanceOf(ForbiddenException.class)
                    .satisfies(e -> assertThat(((ForbiddenException) e).check()).isEqualTo(Check.ROLE));
        }

        @Test
        void allRolesNeedsEveryOne() {
            as(ADMIN);
            assertThatThrownBy(() -> call(OrderApi.class, "allRoles")).isInstanceOf(ForbiddenException.class);
            as(SimplePrincipal.of("x").withRoles("ADMIN", "AUDITOR"));
            assertThatCode(() -> call(OrderApi.class, "allRoles")).doesNotThrowAnyException();
        }

        @Test
        void permissionsRequireAllByDefault() {
            as(SimplePrincipal.of("x").withPermissions("orders:read"));
            assertThatThrownBy(() -> call(OrderApi.class, "export"))
                    .isInstanceOf(ForbiddenException.class)
                    .satisfies(e -> assertThat(((ForbiddenException) e).check()).isEqualTo(Check.PERMISSION));
            as(SimplePrincipal.of("x").withPermissions("orders:read", "orders:export"));
            assertThatCode(() -> call(OrderApi.class, "export")).doesNotThrowAnyException();
        }
    }

    @Nested
    class TypeLevel {

        @Test
        void classAnnotationsApplyToEveryMethod() {
            assertThatThrownBy(() -> call(AccountApi.class, "profile")).isInstanceOf(UnauthenticatedException.class);
            as(SimplePrincipal.of("x"));
            assertThatThrownBy(() -> call(AccountApi.class, "profile")).isInstanceOf(ForbiddenException.class);
            as(ALICE);
            assertThatCode(() -> call(AccountApi.class, "profile")).doesNotThrowAnyException();
        }

        @Test
        void methodAnnotationReplacesClassOne() {
            as(SimplePrincipal.of("x").withRoles("ADMIN"));
            assertThatCode(() -> call(AccountApi.class, "admin")).doesNotThrowAnyException();
        }

        @Test
        void publicAccessOptsOut() {
            assertThatCode(() -> call(AccountApi.class, "login")).doesNotThrowAnyException();
        }
    }

    @Nested
    class Ownership {

        @Test
        void ownerPasses() {
            as(ALICE);
            assertThatCode(() -> call(OrderApi.class, "get", 1L)).doesNotThrowAnyException();
        }

        @Test
        void someoneElsesResourceIs403() {
            as(ALICE);
            assertThatThrownBy(() -> call(OrderApi.class, "get", 2L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Order 2 does not belong to the user")
                    .satisfies(e -> assertThat(((ForbiddenException) e).check()).isEqualTo(Check.OWNERSHIP));
        }

        @Test
        void nullIdIsDenied() {
            as(ALICE);
            assertThatThrownBy(() -> call(OrderApi.class, "get", (Object) null)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        void bypassRoleSkipsTheCheck() {
            as(ADMIN);
            assertThatCode(() -> call(OrderApi.class, "cancel", 2L)).doesNotThrowAnyException();
            assertThatThrownBy(() -> call(OrderApi.class, "get", 2L)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        void idIsReadFromRequestObject() {
            as(ALICE);
            assertThatCode(() -> call(OrderApi.class, "update", new UpdateOrder(1L, "x"))).doesNotThrowAnyException();
            assertThatThrownBy(() -> call(OrderApi.class, "update", new UpdateOrder(2L, "x")))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void severalResourcesAreEachChecked() {
            as(ALICE);
            assertThatCode(() -> call(OrderApi.class, "item", 1L, 5L)).doesNotThrowAnyException();
            assertThatThrownBy(() -> call(OrderApi.class, "item", 1L, 500L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Item 500");
        }

        @Test
        void annotationsOnImplementationApplyWhenCalledThroughInterface() throws Exception {
            as(ALICE);
            var viaInterface = Documents.class.getMethod("read", Long.class);
            assertThatThrownBy(() -> guard.check(viaInterface, DocumentService.class, new Object[] {2L}))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class Tenancy {

        @Test
        void sameTenantPasses() {
            as(SimplePrincipal.of("x").withTenant("acme"));
            assertThatCode(() -> call(OrderApi.class, "invoice", "inv-1")).doesNotThrowAnyException();
        }

        @Test
        void otherTenantIs403() {
            as(SimplePrincipal.of("x").withTenant("acme"));
            assertThatThrownBy(() -> call(OrderApi.class, "invoice", "inv-2"))
                    .isInstanceOf(ForbiddenException.class)
                    .satisfies(e -> assertThat(((ForbiddenException) e).check()).isEqualTo(Check.TENANT));
        }

        @Test
        void userWithoutTenantIsDenied() {
            as(ALICE);
            assertThatThrownBy(() -> call(OrderApi.class, "invoice", "inv-1"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("no tenant");
        }
    }

    @Nested
    class RateLimiting {

        @Test
        void thirdCallInTheWindowIs429() {
            as(ALICE);
            call(OrderApi.class, "limited");
            call(OrderApi.class, "limited");
            assertThatThrownBy(() -> call(OrderApi.class, "limited"))
                    .isInstanceOf(RateLimitExceededException.class)
                    .satisfies(e -> assertThat(((RateLimitExceededException) e).retryAfter()).isPositive());
        }

        @Test
        void eachUserHasItsOwnBucket() {
            as(ALICE);
            call(OrderApi.class, "limited");
            call(OrderApi.class, "limited");
            as(ADMIN);
            assertThatCode(() -> call(OrderApi.class, "limited")).doesNotThrowAnyException();
        }

        @Test
        void anonymousCallersShareOneBucketWithoutAKey() {
            call(OrderApi.class, "limited");
            call(OrderApi.class, "limited");
            assertThatThrownBy(() -> call(OrderApi.class, "limited")).isInstanceOf(RateLimitExceededException.class);
        }

        @Test
        void eachAnonymousKeyHasItsOwnBucket() {
            var address = new AtomicReference<String>("10.0.0.1");
            var keyed = AccessGuard.builder()
                    .principalProvider(java.util.Optional::empty)
                    .anonymousKey(() -> java.util.Optional.ofNullable(address.get()))
                    .build();
            var limited = method(OrderApi.class, "limited");

            keyed.check(limited, OrderApi.class, null);
            keyed.check(limited, OrderApi.class, null);
            assertThatThrownBy(() -> keyed.check(limited, OrderApi.class, null))
                    .isInstanceOf(RateLimitExceededException.class);

            address.set("10.0.0.2");
            assertThatCode(() -> keyed.check(limited, OrderApi.class, null)).doesNotThrowAnyException();
        }

        @Test
        void emptyAnonymousKeyFallsBackToTheSharedBucket() {
            var address = new AtomicReference<String>("10.0.0.1");
            var keyed = AccessGuard.builder()
                    .principalProvider(java.util.Optional::empty)
                    .anonymousKey(() -> java.util.Optional.ofNullable(address.get()))
                    .build();
            var limited = method(OrderApi.class, "limited");

            address.set("");
            keyed.check(limited, OrderApi.class, null);
            address.set(null);
            keyed.check(limited, OrderApi.class, null);
            assertThatThrownBy(() -> keyed.check(limited, OrderApi.class, null))
                    .isInstanceOf(RateLimitExceededException.class);
        }
    }

    @Nested
    class Misconfiguration {

        @Test
        void ownedByWithoutResourceIdFails() {
            as(ALICE);
            assertThatThrownBy(() -> call(OrderApi.class, "missingResourceId", 1L))
                    .isInstanceOf(PolicyDefinitionException.class)
                    .hasMessageContaining("needs a parameter annotated with @ResourceId");
        }

        @Test
        void ambiguousResourceIdFails() {
            as(ALICE);
            assertThatThrownBy(() -> call(OrderApi.class, "ambiguous", 1L, 2L))
                    .isInstanceOf(PolicyDefinitionException.class)
                    .hasMessageContaining("several @ResourceId");
        }

        @Test
        void missingResolverFailsClosed() {
            var bare = AccessGuard.builder()
                    .principalProvider(() -> java.util.Optional.of(ALICE))
                    .build();
            assertThatThrownBy(() -> bare.check(method(OrderApi.class, "get"), OrderApi.class, new Object[] {1L}))
                    .isInstanceOf(PolicyDefinitionException.class)
                    .hasMessageContaining("no OwnershipResolver registered");
        }

        @Test
        void resolverExceptionsPropagateAndAreAudited() {
            var failing = AccessGuard.builder()
                    .principalProvider(() -> java.util.Optional.of(ALICE))
                    .ownership(Order.class, (user, id) -> {
                        throw new IllegalStateException("db down");
                    })
                    .auditListener(events::add)
                    .build();
            assertThatThrownBy(() -> failing.check(method(OrderApi.class, "get"), OrderApi.class, new Object[] {1L}))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(events).singleElement().satisfies(e -> assertThat(e.deniedBy()).isEqualTo(Check.ERROR));
        }
    }

    @Nested
    class Auditing {

        @Test
        void grantsAndDenialsAreReported() {
            as(ALICE);
            call(OrderApi.class, "get", 1L);
            assertThatThrownBy(() -> call(OrderApi.class, "get", 2L)).isInstanceOf(ForbiddenException.class);

            assertThat(events).hasSize(2);
            assertThat(events.get(0).granted()).isTrue();
            assertThat(events.get(0).operation()).isEqualTo("OrderApi#get");
            assertThat(events.get(1).granted()).isFalse();
            assertThat(events.get(1).deniedBy()).isEqualTo(Check.OWNERSHIP);
            assertThat(events.get(1).user()).contains(ALICE);
        }

        @Test
        void failingListenerDoesNotChangeTheDecision() {
            var noisy = AccessGuard.builder()
                    .principalProvider(() -> java.util.Optional.of(ALICE))
                    .auditListener(e -> {
                        throw new RuntimeException("boom");
                    })
                    .build();
            assertThatCode(() -> noisy.check(method(OrderApi.class, "me"), OrderApi.class, new Object[0]))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void policyIsCompiledOnce() throws Exception {
        var m = OrderApi.class.getMethod("get", Long.class);
        assertThat(guard.policyFor(m, OrderApi.class)).isSameAs(guard.policyFor(m, OrderApi.class));
        assertThat(guard.policyFor(m, OrderApi.class).needsArguments()).isTrue();
        assertThat(guard.policyFor(OrderApi.class.getMethod("me"), OrderApi.class).needsArguments()).isFalse();
    }
}
