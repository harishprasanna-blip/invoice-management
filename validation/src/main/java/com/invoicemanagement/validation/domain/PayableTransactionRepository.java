package com.invoicemanagement.validation.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PayableTransaction aggregate.
 * Port (interface) for hexagonal architecture.
 *
 * DDD Pattern: Repository (Port)
 * Bounded Context: ValidationContext
 */
public interface PayableTransactionRepository {

    /**
     * Save payable transaction.
     * Publishes domain events.
     */
    PayableTransaction save(PayableTransaction transaction);

    /**
     * Find transaction by ID.
     */
    Optional<PayableTransaction> findById(UUID transactionId);

    /**
     * Find transaction by invoice ID.
     */
    Optional<PayableTransaction> findByInvoiceId(UUID invoiceId);

    /**
     * Check if transaction exists for invoice.
     */
    boolean existsByInvoiceId(UUID invoiceId);
}
