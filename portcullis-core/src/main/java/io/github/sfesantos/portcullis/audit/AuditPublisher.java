package io.github.sfesantos.portcullis.audit;

import io.github.sfesantos.portcullis.PortcullisException;
import io.github.sfesantos.portcullis.RateLimitExceededException;
import io.github.sfesantos.portcullis.SecurityPrincipal;
import io.github.sfesantos.portcullis.UnauthenticatedException;
import io.github.sfesantos.portcullis.ForbiddenException;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.List;

public final class AuditPublisher {

    private static final System.Logger LOG = System.getLogger(AuditPublisher.class.getName());

    private final AuditListener[] listeners;
    private final Clock clock;

    public AuditPublisher(List<AuditListener> listeners, Clock clock) {
        this.listeners = listeners.toArray(AuditListener[]::new);
        this.clock = clock;
    }

    public boolean isEnabled() {
        return listeners.length > 0;
    }

    public void granted(SecurityPrincipal user, String operation) {
        if (isEnabled()) {
            publish(new AccessEvent(clock.instant(), user, operation, true, null, null));
        }
    }

    public void denied(SecurityPrincipal user, String operation, Check check, String reason) {
        if (isEnabled()) {
            publish(new AccessEvent(clock.instant(), user, operation, false, check, reason));
        }
    }

    public void denied(SecurityPrincipal user, String operation, RuntimeException cause) {
        if (isEnabled()) {
            denied(user, operation, checkOf(cause), String.valueOf(cause.getMessage()));
        }
    }

    private static Check checkOf(RuntimeException cause) {
        return switch (cause) {
            case UnauthenticatedException ignored -> Check.AUTHENTICATION;
            case RateLimitExceededException ignored -> Check.RATE_LIMIT;
            case ForbiddenException forbidden -> forbidden.check();
            case PortcullisException ignored -> Check.POLICY;
            default -> Check.ERROR;
        };
    }

    private void publish(AccessEvent event) {
        for (var listener : listeners) {
            try {
                listener.onAccess(event);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "audit listener " + listener.getClass().getName() + " failed", e);
            }
        }
    }
}
