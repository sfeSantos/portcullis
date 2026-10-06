package io.github.sfesantos.portcullis.audit;

@FunctionalInterface
public interface AuditListener {

    void onAccess(AccessEvent event);
    static AuditListener deniedOnly(AuditListener delegate) {
        return event -> {
            if (!event.granted()) {
                delegate.onAccess(event);
            }
        };
    }
}
