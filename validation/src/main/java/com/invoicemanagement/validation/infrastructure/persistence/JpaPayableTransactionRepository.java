package com.invoicemanagement.validation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository interface.
 */
@Repository
public interface JpaPayableTransactionRepository extends JpaRepository<PayableTransactionEntity, UUID> {

    Optional<PayableTransactionEntity> findByInvoiceId(UUID invoiceId);

    boolean existsByInvoiceId(UUID invoiceId);
}
