package com.invoicemanagement.payment.presentation;

import com.invoicemanagement.payment.domain.PaymentRun;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO for payment run response.
 */
@Getter
@Builder
public class PaymentRunResponse {
    private String paymentRunId;
    private String invoiceId;
    private String invoiceNumber;
    private BigDecimal totalAmount;
    private String currency;
    private String vendorId;
    private LocalDate dueDate;
    private LocalDate scheduledPaymentDate;
    private String paymentMethod;
    private String paymentStatus;
    private String sagaState;
    private List<SagaStepDto> completedSteps;
    private String sapDocumentNumber;
    private String gatewayTransactionId;
    private String approvedBy;
    private Instant approvedAt;
    private Instant executedAt;
    private String failureReason;
    private int retryCount;
    private boolean requiresApproval;
    private boolean isOverdue;

    @Getter
    @Builder
    public static class SagaStepDto {
        private String stepName;
        private String status;
        private Instant completedAt;
        private String result;
    }

    public static PaymentRunResponse from(PaymentRun paymentRun) {
        return PaymentRunResponse.builder()
            .paymentRunId(paymentRun.getPaymentRunId().toString())
            .invoiceId(paymentRun.getInvoiceId().toString())
            .invoiceNumber(paymentRun.getInvoiceNumber())
            .totalAmount(paymentRun.getTotalAmount().getAmount())
            .currency(paymentRun.getTotalAmount().getCurrencyCode())
            .vendorId(paymentRun.getVendorId())
            .dueDate(paymentRun.getDueDate())
            .scheduledPaymentDate(paymentRun.getScheduledPaymentDate())
            .paymentMethod(paymentRun.getPaymentMethod().name())
            .paymentStatus(paymentRun.getPaymentStatus().name())
            .sagaState(paymentRun.getSagaState().name())
            .completedSteps(
                paymentRun.getCompletedSteps().stream()
                    .map(step -> SagaStepDto.builder()
                        .stepName(step.getStepName())
                        .status(step.getStatus())
                        .completedAt(step.getCompletedAt())
                        .result(step.getResult())
                        .build())
                    .collect(Collectors.toList())
            )
            .sapDocumentNumber(paymentRun.getSapDocumentNumber())
            .gatewayTransactionId(paymentRun.getGatewayTransactionId())
            .approvedBy(paymentRun.getApprovedBy())
            .approvedAt(paymentRun.getApprovedAt())
            .executedAt(paymentRun.getExecutedAt())
            .failureReason(paymentRun.getFailureReason())
            .retryCount(paymentRun.getRetryCount())
            .requiresApproval(paymentRun.requiresApproval())
            .isOverdue(paymentRun.isOverdue())
            .build();
    }
}
