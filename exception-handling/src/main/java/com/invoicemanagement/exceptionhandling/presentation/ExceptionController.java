package com.invoicemanagement.exceptionhandling.presentation;

import com.invoicemanagement.exceptionhandling.application.ExceptionService;
import com.invoicemanagement.exceptionhandling.domain.ExceptionCase;
import com.invoicemanagement.exceptionhandling.domain.ResolutionAction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API controller for exception handling operations.
 * Presentation Layer.
 */
@RestController
@RequestMapping("/api/v1/exceptions")
public class ExceptionController {

    private final ExceptionService exceptionService;

    public ExceptionController(ExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    /**
     * Get exception case by ID.
     * GET /api/v1/exceptions/{caseId}
     */
    @GetMapping("/{caseId}")
    public ResponseEntity<ExceptionCase> getCase(@PathVariable UUID caseId) {
        return exceptionService.getCase(caseId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all open cases, sorted by priority.
     * GET /api/v1/exceptions
     */
    @GetMapping
    public ResponseEntity<List<ExceptionService.ExceptionCaseDTO>> getOpenCases() {
        return ResponseEntity.ok(exceptionService.getOpenCases());
    }

    /**
     * Get cases assigned to resolver.
     * GET /api/v1/exceptions/assigned/{assignee}
     */
    @GetMapping("/assigned/{assignee}")
    public ResponseEntity<List<ExceptionService.ExceptionCaseDTO>> getCasesForResolver(
        @PathVariable String assignee
    ) {
        return ResponseEntity.ok(exceptionService.getCasesForResolver(assignee));
    }

    /**
     * Add resolution action.
     * POST /api/v1/exceptions/{caseId}/actions
     */
    @PostMapping("/{caseId}/actions")
    public ResponseEntity<Void> addResolutionAction(
        @PathVariable UUID caseId,
        @RequestBody ResolutionActionRequest request
    ) {
        exceptionService.addResolutionAction(
            caseId,
            request.actionType(),
            request.description(),
            request.userId(),
            request.result()
        );
        return ResponseEntity.accepted().build();
    }

    /**
     * Resolve exception case.
     * POST /api/v1/exceptions/{caseId}/resolve
     */
    @PostMapping("/{caseId}/resolve")
    public ResponseEntity<Void> resolveCase(
        @PathVariable UUID caseId,
        @RequestBody ResolveRequest request
    ) {
        exceptionService.resolveCase(caseId, request.resolverUserId(), request.resolutionNotes());
        return ResponseEntity.accepted().build();
    }

    /**
     * Escalate exception case.
     * POST /api/v1/exceptions/{caseId}/escalate
     */
    @PostMapping("/{caseId}/escalate")
    public ResponseEntity<Void> escalateCase(
        @PathVariable UUID caseId,
        @RequestBody EscalateRequest request
    ) {
        exceptionService.escalateCase(caseId, request.reason());
        return ResponseEntity.accepted().build();
    }

    /**
     * Get dashboard statistics.
     * GET /api/v1/exceptions/dashboard/stats
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<ExceptionService.DashboardStats> getDashboardStats() {
        return ResponseEntity.ok(exceptionService.getDashboardStats());
    }

    /**
     * Health check endpoint.
     * GET /api/v1/exceptions/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("ExceptionHandlingContext", "UP"));
    }

    // Request DTOs

    record ResolutionActionRequest(
        ResolutionAction.ResolutionActionType actionType,
        String description,
        UUID userId,
        String result
    ) {}

    record ResolveRequest(
        UUID resolverUserId,
        String resolutionNotes
    ) {}

    record EscalateRequest(
        String reason
    ) {}

    record HealthResponse(
        String service,
        String status
    ) {}
}
