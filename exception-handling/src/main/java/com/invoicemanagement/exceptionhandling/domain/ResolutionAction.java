package com.invoicemanagement.exceptionhandling.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a resolution action taken on an exception case.
 * Part of ExceptionCase aggregate.
 *
 * DDD Pattern: Entity (within ExceptionCase aggregate)
 * Bounded Context: ExceptionHandlingContext
 */
@Getter
@EqualsAndHashCode(of = "actionId")
@ToString
public class ResolutionAction implements Serializable {

    private final UUID actionId;
    private final ResolutionActionType actionType;
    private final String actionDescription;
    private final UUID performedBy; // User ID or system
    private final Instant performedAt;
    private final boolean automated;
    private final String result; // Success, failure, or details

    public ResolutionAction(
        UUID actionId,
        ResolutionActionType actionType,
        String actionDescription,
        UUID performedBy,
        Instant performedAt,
        boolean automated,
        String result
    ) {
        this.actionId = Objects.requireNonNull(actionId, "Action ID cannot be null");
        this.actionType = Objects.requireNonNull(actionType, "Action type cannot be null");
        this.actionDescription = actionDescription;
        this.performedBy = performedBy;
        this.performedAt = Objects.requireNonNull(performedAt, "Performed timestamp cannot be null");
        this.automated = automated;
        this.result = result;
    }

    /**
     * Factory method for manual action.
     */
    public static ResolutionAction manualAction(
        ResolutionActionType actionType,
        String description,
        UUID userId,
        String result
    ) {
        return new ResolutionAction(
            UUID.randomUUID(),
            actionType,
            description,
            userId,
            Instant.now(),
            false,
            result
        );
    }

    /**
     * Factory method for automated action.
     */
    public static ResolutionAction automatedAction(
        ResolutionActionType actionType,
        String description,
        String result
    ) {
        return new ResolutionAction(
            UUID.randomUUID(),
            actionType,
            description,
            null, // System performed
            Instant.now(),
            true,
            result
        );
    }

    /**
     * Resolution action type enumeration.
     */
    public enum ResolutionActionType {
        INVESTIGATION_STARTED,
        DATA_CORRECTION,
        VENDOR_CONTACTED,
        SAP_UPDATE,
        MANUAL_OVERRIDE,
        APPROVAL_REQUESTED,
        APPROVAL_GRANTED,
        APPROVAL_DENIED,
        PAYMENT_AUTHORIZED,
        INVOICE_REJECTED,
        ESCALATION,
        COMMENT_ADDED
    }
}
