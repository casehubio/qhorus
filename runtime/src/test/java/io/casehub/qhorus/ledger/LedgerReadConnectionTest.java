package io.casehub.qhorus.ledger;

import io.agroal.api.AgroalDataSource;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.jpa.PlainLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LedgerReadConnectionTest {

    @Inject LedgerEntryRepository repository;
    @Inject @DataSource("qhorus") AgroalDataSource dataSource;

    @Test
    void singleEntryLookupsReleaseConnectionBeforeNextWrite() throws Exception {
        var entry = new PlainLedgerEntry();
        entry.subjectId = UUID.randomUUID();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorType = ActorType.SYSTEM;
        repository.save(entry, null);

        assertTrue(repository.findLatestBySubjectId(entry.subjectId, null).isPresent());
        assertTrue(repository.findEntryById(entry.id, null).isPresent());

        var connections = new ArrayList<Connection>();
        try {
            int poolSize = dataSource.getConfiguration().connectionPoolConfiguration().maxSize();
            for (int i = 0; i < poolSize; i++) {
                connections.add(dataSource.getConnection());
            }
        } finally {
            for (var connection : connections) {
                connection.close();
            }
        }
    }
}
