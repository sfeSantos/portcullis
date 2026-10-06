package io.github.sfesantos.portcullis;

import io.github.sfesantos.portcullis.annotation.Authenticated;
import io.github.sfesantos.portcullis.annotation.Match;
import io.github.sfesantos.portcullis.annotation.OwnedBy;
import io.github.sfesantos.portcullis.annotation.PublicAccess;
import io.github.sfesantos.portcullis.annotation.RateLimit;
import io.github.sfesantos.portcullis.annotation.RequiresPermission;
import io.github.sfesantos.portcullis.annotation.RequiresRole;
import io.github.sfesantos.portcullis.annotation.ResourceId;
import io.github.sfesantos.portcullis.annotation.SameTenant;

final class Fixtures {

    private Fixtures() {
    }

    record Order(long id, String owner) {
    }

    record Item(long id) {
    }

    record Invoice(String id) {
    }

    record UpdateOrder(Long orderId, String note) implements HasResourceId<Long> {
        @Override
        public Long resourceId() {
            return orderId;
        }
    }

    static class OrderApi {

        public String open() {
            return "open";
        }

        @Authenticated
        public String me() {
            return "me";
        }

        @RequiresRole({"ADMIN", "SUPPORT"})
        public void anyRole() {
        }

        @RequiresRole(value = {"ADMIN", "AUDITOR"}, match = Match.ALL)
        public void allRoles() {
        }

        @RequiresPermission({"orders:read", "orders:export"})
        public void export() {
        }

        @OwnedBy(Order.class)
        public void get(@ResourceId Long orderId) {
        }

        @OwnedBy(value = Order.class, bypassRoles = "ADMIN")
        public void cancel(@ResourceId Long orderId) {
        }

        @OwnedBy(Order.class)
        public void update(@ResourceId UpdateOrder request) {
        }

        @OwnedBy(Order.class)
        @OwnedBy(Item.class)
        public void item(@ResourceId(Order.class) Long orderId, @ResourceId(Item.class) Long itemId) {
        }

        @SameTenant(Invoice.class)
        public void invoice(@ResourceId String invoiceId) {
        }

        @RateLimit(requests = 2)
        public void limited() {
        }

        @OwnedBy(Order.class)
        public void missingResourceId(Long orderId) {
        }

        @OwnedBy(Item.class)
        public void unregisteredResolver(@ResourceId Long itemId) {
        }

        @OwnedBy(Order.class)
        public void ambiguous(@ResourceId Long a, @ResourceId Long b) {
        }
    }

    @Authenticated
    @RequiresRole("USER")
    static class AccountApi {

        public void profile() {
        }

        @RequiresRole("ADMIN")
        public void admin() {
        }

        @PublicAccess
        public void login() {
        }
    }

    interface Documents {
        void read(Long id);
    }

    static class DocumentService implements Documents {
        @Override
        @OwnedBy(Order.class)
        public void read(@ResourceId Long id) {
        }
    }
}
