package io.casehub.qhorus.runtime.cdi;

import io.casehub.qhorus.api.store.DataStore;
import io.casehub.qhorus.runtime.data.DataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class CdiDataService extends DataService {

    @Inject
    public CdiDataService(DataStore dataStore) {
        super(dataStore);
    }

    CdiDataService() {
        super(null);
    }
}
