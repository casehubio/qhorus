package io.casehub.qhorus.signing;

import io.casehub.qhorus.api.event.BindingVerificationRequestedEvent;
import io.casehub.qhorus.signing.core.BindingVerificationServiceCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class BindingVerificationObserver {

    @Inject
    BindingVerificationServiceCore verificationService;

    void onVerificationRequested(@ObservesAsync BindingVerificationRequestedEvent event) {
        verificationService.verify(event.binding());
    }
}
