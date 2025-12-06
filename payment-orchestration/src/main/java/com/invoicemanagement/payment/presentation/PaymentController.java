package com.invoicemanagement.payment.presentation;

import com.invoicemanagement.payment.application.PaymentOrchestrationService;
import com.invoicemanagement.payment.domain.PaymentRun;
import com.invoicemanagement.sharedkernel.domain.Money;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for Payment Orchestration.
 * 
 * Endpoints:
 * - POST /api/v1/payments - Schedule payment
 * - GET /api/v1/payments/{id} - Get payment details
 * - POST /api/v1/payments/{id}/approve - Approve payment
 * - POST /api/v1/payments/{id}/retry - Retry failed payment
 * - GET /api/v1/payments/pending-approval - Get payments requiring approval
 * - GET /api/v1/payments/scheduled - Get scheduled payments
 * - GET /api/v1/payments/failed - Get failed payments
 * - GET /api/v1/payments/overdue - Get overdue payments
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentOrchestrationService paymentService;

    public PaymentController(PaymentOrchestrationService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Schedule a new payment.
     */
    @PostMapping
    public ResponseEntity<PaymentRunResponse> schedulePayment(
        @RequestBody SchedulePaymentRequest request
    ) {
        UUID paymentRunId = paymentService.schedulePayment(
            UUID.fromString(request.getInvoiceId()),
            request.getInvoiceNumber(),
            Money.of(request.getAmount(), request.getCurrency()),
            request.getVendorId(),
            request.getVendorBankAccount(),
            request.getDueDate(),
            PaymentRun.PaymentMethod.valueOf(request.getPaymentMethod())
        );

        PaymentRun paymentRun = paymentService.getPaymentRun(paymentRunId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PaymentRunResponse.from(paymentRun));
    }

    /**
     * Get payment details by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentRunResponse> getPayment(@PathVariable String id) {
        try {
            PaymentRun paymentRun = paymentService.getPaymentRun(UUID.fromString(id));
            return ResponseEntity.ok(PaymentRunResponse.from(paymentRun));
        } catch (PaymentOrchestrationService.PaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Approve a payment (triggers Saga execution).
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<PaymentRunResponse> approvePayment(
        @PathVariable String id,
        @RequestBody ApprovePaymentRequest request
    ) {
        try {
            paymentService.approveAndExecutePayment(
                UUID.fromString(id),
                request.getApprover(),
                request.getNotes()
            );

            PaymentRun paymentRun = paymentService.getPaymentRun(UUID.fromString(id));
            return ResponseEntity.ok(PaymentRunResponse.from(paymentRun));
        } catch (PaymentOrchestrationService.PaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Retry a failed payment.
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<PaymentRunResponse> retryPayment(@PathVariable String id) {
        try {
            paymentService.retryPayment(UUID.fromString(id));
            PaymentRun paymentRun = paymentService.getPaymentRun(UUID.fromString(id));
            return ResponseEntity.ok(PaymentRunResponse.from(paymentRun));
        } catch (PaymentOrchestrationService.PaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all payments requiring manual approval.
     */
    @GetMapping("/pending-approval")
    public ResponseEntity<List<PaymentRunResponse>> getPendingApprovals() {
        List<PaymentRun> payments = paymentService.getPaymentsRequiringApproval();
        List<PaymentRunResponse> response = payments.stream()
            .map(PaymentRunResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Get scheduled payments for a specific date.
     */
    @GetMapping("/scheduled")
    public ResponseEntity<List<PaymentRunResponse>> getScheduledPayments(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<PaymentRun> payments = paymentService.getScheduledPayments(date);
        List<PaymentRunResponse> response = payments.stream()
            .map(PaymentRunResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Get all failed payments eligible for retry.
     */
    @GetMapping("/failed")
    public ResponseEntity<List<PaymentRunResponse>> getFailedPayments() {
        List<PaymentRun> payments = paymentService.getFailedPayments();
        List<PaymentRunResponse> response = payments.stream()
            .map(PaymentRunResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Get all overdue payments.
     */
    @GetMapping("/overdue")
    public ResponseEntity<List<PaymentRunResponse>> getOverduePayments() {
        List<PaymentRun> payments = paymentService.getOverduePayments();
        List<PaymentRunResponse> response = payments.stream()
            .map(PaymentRunResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Exception handler for global error handling.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(e.getMessage()));
    }

    private record ErrorResponse(String message) {}
}
