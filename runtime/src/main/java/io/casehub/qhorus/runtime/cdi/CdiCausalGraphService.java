package io.casehub.qhorus.runtime.cdi;

import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class CdiCausalGraphService extends CausalGraphService {

    @Inject
    public CdiCausalGraphService(MessageLedgerEntryRepository ledgerRepo, ChannelStore channelStore) {
        super(ledgerRepo, channelStore);
    }

    CdiCausalGraphService() {}
}
