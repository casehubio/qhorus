package io.casehub.qhorus.runtime.ledger;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.ledger.runtime.repository.jpa.JpaLedgerMerkleFrontierRepository;

/**
 * Qhorus's default {@link io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository} bean.
 *
 * <p>Inherits all behaviour from {@link JpaLedgerMerkleFrontierRepository}: frontier
 * read, delete-and-replace for the Merkle tree append operation.
 *
 * <p>Exists for the same CDI reason as {@link QhorusLedgerEntryRepository}: the library
 * class is {@code @Alternative}, so a non-alternative subclass is required to provide a
 * DEFAULT bean. Refs qhorus#255.
 */
@ApplicationScoped
class QhorusLedgerMerkleFrontierRepository extends JpaLedgerMerkleFrontierRepository {
    // Intentionally empty — all behaviour inherited from JpaLedgerMerkleFrontierRepository.
}
