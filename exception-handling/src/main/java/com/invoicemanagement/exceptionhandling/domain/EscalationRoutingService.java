package com.invoicemanagement.exceptionhandling.domain;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Domain service for routing exception cases to appropriate resolvers.
 * Implements escalation logic based on severity, SLA, and case characteristics.
 *
 * DDD Pattern: Domain Service
 * Bounded Context: ExceptionHandlingContext
 */
@Service
public class EscalationRoutingService {

    // Resolver assignment based on exception type and escalation level
    private static final Map<ExceptionCase.ExceptionType, String> L1_RESOLVER_MAP = Map.of(
        ExceptionCase.ExceptionType.MATCHING_MISMATCH, "ap-analyst-team",
        ExceptionCase.ExceptionType.COMPLIANCE_VIOLATION, "compliance-team",
        ExceptionCase.ExceptionType.EXTRACTION_FAILED, "ai-ops-team",
        ExceptionCase.ExceptionType.PAYMENT_FAILED, "treasury-team"
    );

    private static final Map<ExceptionCase.ExceptionType, String> L2_RESOLVER_MAP = Map.of(
        ExceptionCase.ExceptionType.MATCHING_MISMATCH, "senior-ap-analyst",
        ExceptionCase.ExceptionType.COMPLIANCE_VIOLATION, "compliance-manager",
        ExceptionCase.ExceptionType.EXTRACTION_FAILED, "ai-engineering-lead",
        ExceptionCase.ExceptionType.PAYMENT_FAILED, "treasury-manager"
    );

    /**
     * Route exception case to appropriate resolver.
     *
     * @param exceptionCase Case to route
     * @return Assignee identifier
     */
    public String routeCase(ExceptionCase exceptionCase) {
        // Check if case should be escalated based on SLA
        if (shouldAutoEscalate(exceptionCase)) {
            return routeEscalatedCase(exceptionCase);
        }

        // Route based on escalation level
        return switch (exceptionCase.getEscalationLevel()) {
            case L0_AUTOMATED -> "automation-system";
            case L1_ANALYST -> routeToL1Analyst(exceptionCase);
            case L2_SPECIALIST -> routeToL2Specialist(exceptionCase);
            case L3_MANAGER -> routeToManager(exceptionCase);
            case L4_EXECUTIVE -> "executive-team";
        };
    }

    /**
     * Determine if case should be auto-escalated based on SLA.
     */
    public boolean shouldAutoEscalate(ExceptionCase exceptionCase) {
        Duration timeUntilSLA = exceptionCase.getTimeUntilSLA();

        // Escalate if SLA breached
        if (exceptionCase.isSlaBreached()) {
            return true;
        }

        // Escalate if critical severity and approaching SLA (< 1 hour)
        if (exceptionCase.getSeverity() == ExceptionCase.ExceptionSeverity.CRITICAL
            && timeUntilSLA.toHours() < 1) {
            return true;
        }

        // Escalate if high severity and close to SLA (< 4 hours)
        if (exceptionCase.getSeverity() == ExceptionCase.ExceptionSeverity.HIGH
            && timeUntilSLA.toHours() < 4) {
            return true;
        }

        return false;
    }

    /**
     * Determine escalation level for a case.
     */
    public ExceptionCase.EscalationLevel determineEscalationLevel(
        ExceptionCase exceptionCase,
        boolean autoResolveAttempted,
        boolean autoResolveFailed
    ) {
        ExceptionCase.EscalationLevel currentLevel = exceptionCase.getEscalationLevel();

        // If auto-resolve failed, escalate to L1
        if (autoResolveAttempted && autoResolveFailed) {
            return ExceptionCase.EscalationLevel.L1_ANALYST;
        }

        // If critical severity and SLA breached, escalate to manager
        if (exceptionCase.getSeverity() == ExceptionCase.ExceptionSeverity.CRITICAL
            && exceptionCase.isSlaBreached()) {
            return ExceptionCase.EscalationLevel.L3_MANAGER;
        }

        // If case has been at current level for too long, escalate
        if (shouldEscalateDueToAge(exceptionCase)) {
            return escalateOneLevel(currentLevel);
        }

        return currentLevel;
    }

    /**
     * Check if case should escalate due to age at current level.
     */
    private boolean shouldEscalateDueToAge(ExceptionCase exceptionCase) {
        if (exceptionCase.getAssignedAt() == null) {
            return false;
        }

        Duration timeAtCurrentLevel = Duration.between(exceptionCase.getAssignedAt(), Instant.now());

        return switch (exceptionCase.getEscalationLevel()) {
            case L0_AUTOMATED -> timeAtCurrentLevel.toMinutes() > 15; // 15 min at L0
            case L1_ANALYST -> timeAtCurrentLevel.toHours() > 4; // 4 hours at L1
            case L2_SPECIALIST -> timeAtCurrentLevel.toHours() > 12; // 12 hours at L2
            case L3_MANAGER -> timeAtCurrentLevel.toHours() > 24; // 24 hours at L3
            case L4_EXECUTIVE -> false; // Max level
        };
    }

    /**
     * Route escalated case to appropriate resolver.
     */
    private String routeEscalatedCase(ExceptionCase exceptionCase) {
        ExceptionCase.EscalationLevel escalatedLevel = escalateOneLevel(exceptionCase.getEscalationLevel());

        return switch (escalatedLevel) {
            case L0_AUTOMATED -> "automation-system";
            case L1_ANALYST -> routeToL1Analyst(exceptionCase);
            case L2_SPECIALIST -> routeToL2Specialist(exceptionCase);
            case L3_MANAGER -> routeToManager(exceptionCase);
            case L4_EXECUTIVE -> "executive-team";
        };
    }

    /**
     * Route to L1 analyst based on exception type.
     */
    private String routeToL1Analyst(ExceptionCase exceptionCase) {
        return L1_RESOLVER_MAP.getOrDefault(
            exceptionCase.getExceptionType(),
            "general-ap-team"
        );
    }

    /**
     * Route to L2 specialist based on exception type.
     */
    private String routeToL2Specialist(ExceptionCase exceptionCase) {
        return L2_RESOLVER_MAP.getOrDefault(
            exceptionCase.getExceptionType(),
            "senior-ap-analyst"
        );
    }

    /**
     * Route to manager level.
     */
    private String routeToManager(ExceptionCase exceptionCase) {
        return switch (exceptionCase.getExceptionType()) {
            case COMPLIANCE_VIOLATION -> "compliance-director";
            case PAYMENT_FAILED -> "cfo-office";
            default -> "ap-manager";
        };
    }

    /**
     * Escalate one level up.
     */
    private ExceptionCase.EscalationLevel escalateOneLevel(ExceptionCase.EscalationLevel currentLevel) {
        return switch (currentLevel) {
            case L0_AUTOMATED -> ExceptionCase.EscalationLevel.L1_ANALYST;
            case L1_ANALYST -> ExceptionCase.EscalationLevel.L2_SPECIALIST;
            case L2_SPECIALIST -> ExceptionCase.EscalationLevel.L3_MANAGER;
            case L3_MANAGER -> ExceptionCase.EscalationLevel.L4_EXECUTIVE;
            case L4_EXECUTIVE -> ExceptionCase.EscalationLevel.L4_EXECUTIVE; // Max level
        };
    }

    /**
     * Calculate priority score for case sorting.
     * Higher score = higher priority.
     */
    public int calculatePriorityScore(ExceptionCase exceptionCase) {
        int score = 0;

        // Severity contribution (40 points max)
        score += switch (exceptionCase.getSeverity()) {
            case CRITICAL -> 40;
            case HIGH -> 30;
            case MEDIUM -> 20;
            case LOW -> 10;
        };

        // SLA urgency contribution (30 points max)
        Duration timeUntilSLA = exceptionCase.getTimeUntilSLA();
        if (exceptionCase.isSlaBreached()) {
            score += 30;
        } else if (timeUntilSLA.toHours() < 1) {
            score += 25;
        } else if (timeUntilSLA.toHours() < 4) {
            score += 20;
        } else if (timeUntilSLA.toHours() < 12) {
            score += 15;
        } else {
            score += 10;
        }

        // Escalation level contribution (20 points max)
        score += switch (exceptionCase.getEscalationLevel()) {
            case L4_EXECUTIVE -> 20;
            case L3_MANAGER -> 15;
            case L2_SPECIALIST -> 10;
            case L1_ANALYST -> 5;
            case L0_AUTOMATED -> 0;
        };

        // Age contribution (10 points max)
        Duration age = Duration.between(exceptionCase.getCreatedAt(), Instant.now());
        if (age.toDays() > 7) {
            score += 10;
        } else if (age.toDays() > 3) {
            score += 7;
        } else if (age.toHours() > 24) {
            score += 5;
        } else {
            score += 2;
        }

        return score;
    }
}
