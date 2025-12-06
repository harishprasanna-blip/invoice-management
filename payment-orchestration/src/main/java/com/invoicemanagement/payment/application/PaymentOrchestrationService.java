package com.invoicemanagement.payment.application;

import com.invoicemanagement.payment.domain.PaymentRun;
import com.invoicemanagement.payment.domain.PaymentRunRepository;
import com.invoicemanagement.payment.infrastructure.gateway.PaymentGatewayClient;
import com.invoicemanagement.payment.infrastructure.gateway.PaymentGatewayResponse;
import com.invoicemanagement.payment.infrastructure.sap.SAPAccountingClient;
import com.invoicemanagement.payment.infrastructure.sap.SAPAccountingDocumentResponse;
import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application Service orchestrating payment processing with Saga pattern.
 * 
 * Saga Steps:
 * 1. Schedule Payment
 * 2. Approve Payment (manual or automatic based on amount)
 * 3. Post to SAP Accounting
 * 4. Execute Payment via Gateway
 * 
 * Compensating Actions:
 * - Reverse SAP posting if payment fails
 * - Cancel gateway transaction if possible
 * 
 * DDD Pattern: Application Service
 * Architecture Pattern: Saga Orchestrator
 */
@Service
public class PaymentOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentOrchestrationService.class);

    private final PaymentRunRepository paymentRepository;
    private final SAPAccountingClient sapClient;
    private final PaymentGatewayClient gatewayClient;

    public PaymentOrchestrationService(
        PaymentRunRepository paymentRepository,
        SAPAccountingClient sapClient,
        PaymentGatewayClient gatewayClient
    ) {
        this.paymentRepository = paymentRepository;
        this.sapClient = sapClient;
        this.gatewayClient = gatewayClient;
    }

    /**
     * Saga Step 1: Schedule a payment run.
     * Triggered by ValidationPassedEvent from ValidationContext.
     */
    @Transactional
    public UUID schedulePayment(
        UUID invoiceId,
        String invoiceNumber,
        Money totalAmount,
        String vendorId,
        String vendorBankAccount,
        LocalDate dueDate,
        PaymentRun.PaymentMethod paymentMethod
    ) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        PaymentRun paymentRun = PaymentRun.schedule(
            tenantId,
            invoiceId,
            invoiceNumber,
            totalAmount,
            vendorId,
            vendorBankAccount,
            dueDate,
            paymentMethod
        );

        paymentRun = paymentRepository.save(paymentRun);

        logger.info("Payment scheduled: {} for invoice {} (amount: {})",
            paymentRun.getPaymentRunId(), invoiceNumber, totalAmount);

        // If auto-approval eligible, proceed immediately
        if (!paymentRun.requiresApproval()) {
            approveAndExecutePayment(paymentRun.getPaymentRunId(), "SYSTEM", "Auto-approved");
        }

        return paymentRun.getPaymentRunId();
    }

    /**
     * Saga Step 2: Approve payment and trigger execution.
     * Can be manual or automatic based on business rules.
     */
    @Transactional
    public void approveAndExecutePayment(UUID paymentRunId, String approver, String notes) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        PaymentRun paymentRun = paymentRepository.findById(paymentRunId, tenantId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment run not found: " + paymentRunId));

        // Step 2a: Approve
        paymentRun.approve(approver, notes);
        paymentRun = paymentRepository.save(paymentRun);

        logger.info("Payment approved: {} by {}", paymentRunId, approver);

        // Continue Saga orchestration
        try {
            // Step 3: Post to SAP
            postToSAP(paymentRun);

            // Step 4: Execute payment
            executePayment(paymentRun);

        } catch (Exception e) {
            logger.error("Saga failed for payment {}: {}", paymentRunId, e.getMessage(), e);
            handleSagaFailure(paymentRun, e);
        }
    }

    /**
     * Saga Step 3: Post payment document to SAP accounting.
     */
    private void postToSAP(PaymentRun paymentRun) {
        String tenantId = paymentRun.getTenantId().getId().toString();

        try {
            SAPAccountingDocumentResponse sapResponse = sapClient.postPaymentDocument(
                paymentRun,
                tenantId
            );

            if ("SUCCESS".equals(sapResponse.getStatus())) {
                paymentRun.postToSAP(
                    sapResponse.getDocumentNumber(),
                    sapResponse.getMessage()
                );
                paymentRepository.save(paymentRun);

                logger.info("SAP posting successful for payment {}: document {}",
                    paymentRun.getPaymentRunId(), sapResponse.getDocumentNumber());
            } else {
                throw new RuntimeException("SAP posting failed: " + sapResponse.getMessage());
            }

        } catch (Exception e) {
            logger.error("SAP posting failed for payment {}", paymentRun.getPaymentRunId(), e);
            throw new SAPPostingException("SAP posting failed", e);
        }
    }

    /**
     * Saga Step 4: Execute payment via gateway.
     */
    private void executePayment(PaymentRun paymentRun) {
        String tenantId = paymentRun.getTenantId().getId().toString();

        try {
            PaymentGatewayResponse gatewayResponse = gatewayClient.executePayment(
                paymentRun,
                tenantId
            );

            if ("SUCCESS".equals(gatewayResponse.getStatus())) {
                paymentRun.executePayment(
                    gatewayResponse.getTransactionId(),
                    gatewayResponse.getMessage()
                );
                paymentRepository.save(paymentRun);

                logger.info("Payment executed successfully: {} (txn: {})",
                    paymentRun.getPaymentRunId(), gatewayResponse.getTransactionId());
            } else {
                throw new RuntimeException("Payment execution failed: " + gatewayResponse.getMessage());
            }

        } catch (Exception e) {
            logger.error("Payment execution failed for {}", paymentRun.getPaymentRunId(), e);
            throw new PaymentExecutionException("Payment execution failed", e);
        }
    }

    /**
     * Saga Compensation: Handle failure and execute compensating actions.
     */
    private void handleSagaFailure(PaymentRun paymentRun, Exception cause) {
        String tenantId = paymentRun.getTenantId().getId().toString();

        // Mark payment as failed
        String failedStep = determineFailedStep(cause);
        paymentRun.fail(cause.getMessage(), failedStep);
        paymentRepository.save(paymentRun);

        logger.warn("Starting compensation for failed payment: {}", paymentRun.getPaymentRunId());

        // Execute compensating actions
        for (PaymentRun.CompensationAction action : paymentRun.getCompensationActions()) {
            try {
                if ("REVERSE_SAP_POSTING".equals(action.getActionName())) {
                    sapClient.reverseDocument(
                        action.getTargetResource(),
                        "Payment execution failed: " + cause.getMessage(),
                        tenantId
                    );
                    logger.info("Compensated: Reversed SAP document {}", action.getTargetResource());
                }
            } catch (Exception e) {
                logger.error("Compensation action failed: {}", action.getActionName(), e);
                // Continue with other compensations even if one fails
            }
        }

        paymentRun.compensationCompleted();
        paymentRepository.save(paymentRun);

        logger.info("Compensation completed for payment: {}", paymentRun.getPaymentRunId());
    }

    /**
     * Retry a failed payment.
     */
    @Transactional
    public void retryPayment(UUID paymentRunId) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        PaymentRun paymentRun = paymentRepository.findById(paymentRunId, tenantId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment run not found: " + paymentRunId));

        paymentRun.retry();
        paymentRepository.save(paymentRun);

        logger.info("Payment retry initiated: {} (attempt {})",
            paymentRunId, paymentRun.getRetryCount());

        // Re-execute from approval step
        approveAndExecutePayment(paymentRunId, "SYSTEM", "Retry attempt " + paymentRun.getRetryCount());
    }

    /**
     * Get payment run by ID.
     */
    @Transactional(readOnly = true)
    public PaymentRun getPaymentRun(UUID paymentRunId) {
        TenantId tenantId = TenantContext.getCurrentTenant();
        return paymentRepository.findById(paymentRunId, tenantId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment run not found: " + paymentRunId));
    }

    /**
     * Get all payments requiring approval.
     */
    @Transactional(readOnly = true)
    public List<PaymentRun> getPaymentsRequiringApproval() {
        TenantId tenantId = TenantContext.getCurrentTenant();
        return paymentRepository.findPaymentsRequiringApproval(tenantId);
    }

    /**
     * Get scheduled payments for a date.
     */
    @Transactional(readOnly = true)
    public List<PaymentRun> getScheduledPayments(LocalDate date) {
        TenantId tenantId = TenantContext.getCurrentTenant();
        return paymentRepository.findScheduledPayments(date, tenantId);
    }

    /**
     * Get failed payments eligible for retry.
     */
    @Transactional(readOnly = true)
    public List<PaymentRun> getFailedPayments() {
        TenantId tenantId = TenantContext.getCurrentTenant();
        return paymentRepository.findFailedPayments(tenantId);
    }

    /**
     * Get overdue payments.
     */
    @Transactional(readOnly = true)
    public List<PaymentRun> getOverduePayments() {
        TenantId tenantId = TenantContext.getCurrentTenant();
        return paymentRepository.findOverduePayments(tenantId);
    }

    private String determineFailedStep(Exception cause) {
        if (cause instanceof SAPPostingException) {
            return "SAP_POSTING";
        } else if (cause instanceof PaymentExecutionException) {
            return "PAYMENT_EXECUTION";
        }
        return "UNKNOWN";
    }

    // Exception classes
    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }

    public static class SAPPostingException extends RuntimeException {
        public SAPPostingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class PaymentExecutionException extends RuntimeException {
        public PaymentExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
