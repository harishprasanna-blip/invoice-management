package com.invoicemanagement.payment.infrastructure.gateway;

import com.invoicemanagement.payment.domain.PaymentRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

/**
 * Client for payment gateway integration.
 * Executes actual payment transactions via third-party gateway.
 * 
 * Supports: ACH, Wire Transfer, Check, Virtual Card
 */
@Component
public class PaymentGatewayClient {

    private static final Logger logger = LoggerFactory.getLogger(PaymentGatewayClient.class);

    private final WebClient gatewayWebClient;

    public PaymentGatewayClient(
        @Value("${payment.gateway.base-url}") String gatewayBaseUrl,
        @Value("${payment.gateway.api-key}") String apiKey
    ) {
        this.gatewayWebClient = WebClient.builder()
            .baseUrl(gatewayBaseUrl)
            .defaultHeaders(headers -> {
                headers.set("Authorization", "Bearer " + apiKey);
                headers.set("Content-Type", "application/json");
            })
            .build();
    }

    /**
     * Execute payment via gateway.
     * Uses idempotency key to prevent duplicate payments.
     */
    public PaymentGatewayResponse executePayment(
        PaymentRun paymentRun,
        String tenantId
    ) {
        PaymentGatewayRequest request = buildPaymentRequest(paymentRun);

        try {
            return gatewayWebClient.post()
                .uri("/api/v1/payments")
                .header("X-Tenant-ID", tenantId)
                .header("Idempotency-Key", request.getIdempotencyKey())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentGatewayResponse.class)
                .doOnError(error -> logger.error(
                    "Payment gateway error for payment {}: {}",
                    paymentRun.getPaymentRunId(),
                    error.getMessage()
                ))
                .block();

        } catch (Exception e) {
            logger.error("Payment execution failed for {}", paymentRun.getPaymentRunId(), e);
            throw new PaymentGatewayException(
                "Payment execution failed: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Check status of previously submitted payment.
     */
    public PaymentGatewayResponse checkPaymentStatus(String transactionId, String tenantId) {
        try {
            return gatewayWebClient.get()
                .uri("/api/v1/payments/{txnId}", transactionId)
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(PaymentGatewayResponse.class)
                .block();

        } catch (Exception e) {
            logger.error("Failed to check payment status for {}", transactionId, e);
            throw new PaymentGatewayException(
                "Failed to check payment status: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Cancel a pending payment (if supported by gateway).
     */
    public void cancelPayment(String transactionId, String tenantId) {
        try {
            gatewayWebClient.post()
                .uri("/api/v1/payments/{txnId}/cancel", transactionId)
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(Void.class)
                .block();

            logger.info("Successfully cancelled payment: {}", transactionId);

        } catch (Exception e) {
            logger.error("Failed to cancel payment {}", transactionId, e);
            throw new PaymentGatewayException(
                "Failed to cancel payment: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Build gateway-specific payment request from domain model.
     */
    private PaymentGatewayRequest buildPaymentRequest(PaymentRun paymentRun) {
        PaymentGatewayRequest request = new PaymentGatewayRequest();
        
        request.setPaymentMethod(paymentRun.getPaymentMethod().name());
        request.setAmount(paymentRun.getTotalAmount().getAmount());
        request.setCurrency(paymentRun.getTotalAmount().getCurrencyCode());
        
        // Beneficiary info (in production, would fetch from vendor master)
        request.setBeneficiaryName("Vendor-" + paymentRun.getVendorId());
        request.setBeneficiaryAccount(paymentRun.getVendorBankAccount());
        request.setBeneficiaryBank("BANK001");  // Mock value
        request.setBeneficiaryRoutingNumber("021000021");  // Mock value
        
        // Remittance info
        request.setInvoiceNumber(paymentRun.getInvoiceNumber());
        request.setReference("Payment for invoice " + paymentRun.getInvoiceNumber());
        request.setDescription("Automated AP payment");
        
        // Idempotency key prevents duplicate payments
        request.setIdempotencyKey(paymentRun.getPaymentRunId().toString());
        
        return request;
    }

    // Exception class
    public static class PaymentGatewayException extends RuntimeException {
        public PaymentGatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
