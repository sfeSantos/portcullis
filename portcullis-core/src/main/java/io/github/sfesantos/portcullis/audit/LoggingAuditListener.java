package io.github.sfesantos.portcullis.audit;

import io.github.sfesantos.portcullis.SecurityPrincipal;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

public final class LoggingAuditListener implements AuditListener {
    private static final Logger LOG = System.getLogger("io.github.sfesantos.portcullis.audit");

    @Override
    public void onAccess(AccessEvent event) {
        var user = event.user().map(SecurityPrincipal::id).orElse("anonymous");

        if (event.granted()) {
            LOG.log(Level.DEBUG, "access granted: user={0} operation={1}", user, event.operation());
        } else {
            LOG.log(Level.WARNING, "access denied: user={0} operation={1} check={2} reason={3}",
                    user, event.operation(), event.deniedBy(), event.reason());
        }
    }
}
