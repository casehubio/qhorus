package io.casehub.qhorus.signing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.signing.SigningProvider;
import io.casehub.qhorus.api.spi.AgentCardSigner;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import io.casehub.qhorus.signing.core.BindingVerificationServiceCore;
import io.casehub.qhorus.signing.core.JwsAgentCardSignerCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class AgentCardSigningBeans {

    @Produces
    @ApplicationScoped
    public JwsAgentCardSignerCore agentCardSigner(SigningConfig config, ObjectMapper mapper,
                                                   SigningProvider signingProvider) {
        return new JwsAgentCardSignerCore(config.keyId(), mapper, signingProvider, config.actorId());
    }

    @Produces
    @ApplicationScoped
    public BindingVerificationServiceCore verificationService(AgentCardSigner signer,
                                                               ExternalAgentBindingStore store,
                                                               ObjectMapper mapper,
                                                               SigningConfig config) {
        return new BindingVerificationServiceCore(signer, store, mapper,
                config.jwksCache().connectTimeoutMs(), config.jwksCache().readTimeoutMs());
    }
}
