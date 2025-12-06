package com.invoicemanagement.exceptionhandling.application;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import com.invoicemanagement.exceptionhandling.domain.*;
import com.invoicemanagement.exceptionhandling.infrastructure.ml.MLResolutionServiceClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service coordinating exception handling workflows.
 * Orchestrates domain services and infrastructure adapters.
 *
 * DDD Pattern: Application Service
 * Bounded Context: ExceptionHandlingContext
 */
@Service
public class ExceptionService {

    private final ExceptionCaseRepository caseRepository;
    private final EscalationRoutingService escalationService;
    private final MLResolutionServiceClient mlClient;

    public ExceptionService(
        ExceptionCaseRepository caseRepository,
        EscalationRoutingService escalationService,
        MLResolutionServiceClient mlClient
    ) {
        this.caseRepository = caseRepository;
        this.escalationService = escalationService;
        this.mlClient = mlClient;
    }

    /**
     * Create exception case from external event.
     * Triggered by MismatchDetected, ComplianceViolation, ExtractionFailed events.
     */
    @Transactional
    public UUID createException(
        ExceptionCase.ExceptionType exceptionType,
        ExceptionCase.ExceptionSeverity severity,
        UUID sourceAggregateId,
        String sourceContext,
        String exceptionDetails
    ) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        // Create exception case
        ExceptionCase exceptionCase = ExceptionCase.create(
            tenantId,
            exceptionType,
            severity,
            sourceAggregateId,
            sourceContext,
            exceptionDetails
        );

        // Save (publishes ExceptionCreatedEvent)
        exceptionCase = caseRepository.save(exceptionCase);

        // Get ML recommendation asynchronously
        try {
            MLRecommendation recommendation = mlClient.getRecommendation(exceptionCase);
            exceptionCase.addMLRecommendation(recommendation);

            // If high confidence and automatable, auto-assign to automation system
            if (recommendation.canAutoExecute()) {
                exceptionCase.assign("automation-system", ExceptionCase.EscalationLevel.L0_AUTOMATED);
            } else {
                // Route to appropriate resolver
                String assignee = escalationService.routeCase(exceptionCase);
                exceptionCase.assign(assignee, exceptionCase.getEscalationLevel());
            }

            caseRepository.save(exceptionCase);

        } catch (Exception e) {
            // ML recommendation failed, route normally
            String assignee = escalationService.routeCase(exceptionCase);
            exceptionCase.assign(assignee, exceptionCase.getEscalationLevel());
            caseRepository.save(exceptionCase);
        }

        return exceptionCase.getCaseId();
    }

    /**
     * Get exception case by ID.
     */
    @Transactional(readOnly = true)
    public Optional<ExceptionCase> getCase(UUID caseId) {
        return caseRepository.findById(caseId);
    }

    /**
     * Get all open cases, sorted by priority.
     */
    @Transactional(readOnly = true)
    public List<ExceptionCaseDTO> getOpenCases() {
        List<ExceptionCase> cases = caseRepository.findOpenCases();

        // Sort by priority score
        return cases.stream()
            .sorted(Comparator.comparingInt(escalationService::calculatePriorityScore).reversed())
            .map(this::toCaseDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get cases assigned to specific resolver.
     */
    @Transactional(readOnly = true)
    public List<ExceptionCaseDTO> getCasesForResolver(String assignee) {
        return caseRepository.findByAssignee(assignee).stream()
            .map(this::toCaseDTO)
            .collect(Collectors.toList());
    }

    /**
     * Add resolution action to case.
     */
    @Transactional
    public void addResolutionAction(
        UUID caseId,
        ResolutionAction.ResolutionActionType actionType,
        String description,
        UUID userId,
        String result
    ) {
        ExceptionCase exceptionCase = caseRepository.findById(caseId)
            .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        ResolutionAction action = ResolutionAction.manualAction(actionType, description, userId, result);
        exceptionCase.addResolutionAction(action);

        caseRepository.save(exceptionCase);
    }

    /**
     * Resolve exception case.
     */
    @Transactional
    public void resolveCase(UUID caseId, UUID resolverUserId, String resolutionNotes) {
        ExceptionCase exceptionCase = caseRepository.findById(caseId)
            .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        exceptionCase.resolve(resolverUserId, resolutionNotes);

        // Save (publishes ExceptionResolvedEvent)
        caseRepository.save(exceptionCase);
    }

    /**
     * Escalate case to higher level.
     */
    @Transactional
    public void escalateCase(UUID caseId, String reason) {
        ExceptionCase exceptionCase = caseRepository.findById(caseId)
            .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        ExceptionCase.EscalationLevel newLevel = escalationService.determineEscalationLevel(
            exceptionCase,
            false,
            false
        );

        if (newLevel != exceptionCase.getEscalationLevel()) {
            exceptionCase.escalate(newLevel, reason);

            // Route to new resolver
            String newAssignee = escalationService.routeCase(exceptionCase);
            exceptionCase.assign(newAssignee, newLevel);

            // Save (publishes ExceptionEscalatedEvent)
            caseRepository.save(exceptionCase);
        }
    }

    /**
     * Check and escalate overdue cases (scheduled task).
     */
    @Transactional
    public void processOverdueCases() {
        Instant threshold = Instant.now().minus(java.time.Duration.ofHours(4));
        List<ExceptionCase> overdueCases = caseRepository.findCasesRequiringEscalation(threshold);

        for (ExceptionCase exceptionCase : overdueCases) {
            if (escalationService.shouldAutoEscalate(exceptionCase)) {
                escalateCase(exceptionCase.getCaseId(), "Auto-escalation due to SLA");
            }
        }
    }

    /**
     * Check SLA status for all open cases (scheduled task).
     */
    @Transactional
    public void checkSLAStatus() {
        List<ExceptionCase> openCases = caseRepository.findOpenCases();

        for (ExceptionCase exceptionCase : openCases) {
            exceptionCase.checkSLA();
            caseRepository.save(exceptionCase); // Publishes SLABreachedEvent if needed
        }
    }

    /**
     * Get dashboard statistics.
     */
    @Transactional(readOnly = true)
    public DashboardStats getDashboardStats() {
        List<ExceptionCase> openCases = caseRepository.findOpenCases();
        List<ExceptionCase> slaBreached = caseRepository.findSLABreachedCases();

        long criticalCount = openCases.stream()
            .filter(c -> c.getSeverity() == ExceptionCase.ExceptionSeverity.CRITICAL)
            .count();

        long highCount = openCases.stream()
            .filter(c -> c.getSeverity() == ExceptionCase.ExceptionSeverity.HIGH)
            .count();

        return new DashboardStats(
            openCases.size(),
            slaBreached.size(),
            criticalCount,
            highCount
        );
    }

    private ExceptionCaseDTO toCaseDTO(ExceptionCase exceptionCase) {
        return new ExceptionCaseDTO(
            exceptionCase.getCaseId(),
            exceptionCase.getExceptionType(),
            exceptionCase.getSeverity(),
            exceptionCase.getStatus(),
            exceptionCase.getSourceAggregateId(),
            exceptionCase.getExceptionDetails(),
            exceptionCase.getEscalationLevel(),
            exceptionCase.getAssignedTo(),
            exceptionCase.getTimeUntilSLA(),
            exceptionCase.isSlaBreached(),
            exceptionCase.getCreatedAt(),
            escalationService.calculatePriorityScore(exceptionCase)
        );
    }

    // DTOs

    public record ExceptionCaseDTO(
        UUID caseId,
        ExceptionCase.ExceptionType exceptionType,
        ExceptionCase.ExceptionSeverity severity,
        ExceptionCase.ExceptionStatus status,
        UUID sourceAggregateId,
        String exceptionDetails,
        ExceptionCase.EscalationLevel escalationLevel,
        String assignedTo,
        java.time.Duration timeUntilSLA,
        boolean slaBreached,
        Instant createdAt,
        int priorityScore
    ) {}

    public record DashboardStats(
        long totalOpenCases,
        long slaBreachedCases,
        long criticalCases,
        long highSeverityCases
    ) {}
}
