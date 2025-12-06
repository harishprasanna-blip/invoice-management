package com.invoicemanagement.validation.infrastructure.events;

import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import com.invoicemanagement.validation.application.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.UUID;

/**
 * Event listener for InvoiceExtractedEvent from InvoiceIngestionContext.
 * Polls event_store for unprocessed InvoiceExtractedEvent and triggers validation.
 *
 * Infrastructure Layer - Event Choreography
 */
@Component
public class InvoiceExtractedEventListener {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceExtractedEventListener.class);
    private static final String SUBSCRIPTION_NAME = "ValidationContext.InvoiceExtracted";

    private final DataSource dataSource;
    private final ValidationService validationService;

    public InvoiceExtractedEventListener(
        DataSource dataSource,
        ValidationService validationService
    ) {
        this.dataSource = dataSource;
        this.validationService = validationService;
    }

    /**
     * Poll for InvoiceExtractedEvent every 5 seconds.
     * Production: Use dedicated event processor with configurable polling.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    @Transactional
    public void pollForEvents() {
        try {
            processInvoiceExtractedEvents();
        } catch (Exception e) {
            logger.error("Error processing InvoiceExtractedEvent", e);
        }
    }

    private void processInvoiceExtractedEvents() throws SQLException {
        // Query event_store for unprocessed InvoiceExtractedEvent
        String query = """
            SELECT e.event_id, e.aggregate_id, e.tenant_id, e.event_payload
            FROM common.event_store e
            LEFT JOIN common.event_subscriptions s
                ON e.event_id = s.event_id
                AND s.subscription_name = ?
            WHERE e.event_type = 'InvoiceExtracted'
            AND s.event_id IS NULL
            ORDER BY e.occurred_at
            LIMIT 10
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, SUBSCRIPTION_NAME);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID eventId = UUID.fromString(rs.getString("event_id"));
                UUID aggregateId = UUID.fromString(rs.getString("aggregate_id"));
                UUID tenantId = UUID.fromString(rs.getString("tenant_id"));
                String eventPayload = rs.getString("event_payload");

                try {
                    processEvent(eventId, aggregateId, tenantId, eventPayload);
                    markEventProcessed(eventId, conn);
                } catch (Exception e) {
                    logger.error("Failed to process event: {}", eventId, e);
                    markEventFailed(eventId, e.getMessage(), conn);
                }
            }
        }
    }

    private void processEvent(
        UUID eventId,
        UUID invoiceId,
        UUID tenantId,
        String eventPayload
    ) {
        logger.info("Processing InvoiceExtractedEvent for invoice: {}", invoiceId);

        // Set tenant context for multi-tenancy
        TenantContext.setCurrentTenant(TenantId.of(tenantId));

        try {
            // Parse event payload (simplified - in production use Jackson)
            // For now, assume payload contains: invoiceNumber, totalAmount, currency, poNumber, grNumber
            // In real implementation, this would deserialize a proper InvoiceExtractedEvent DTO

            // Simplified extraction - in production parse JSON properly
            String invoiceNumber = extractField(eventPayload, "invoiceNumber");
            String totalAmountStr = extractField(eventPayload, "totalAmount");
            String currencyCode = extractField(eventPayload, "currency");
            String poNumber = extractField(eventPayload, "poNumber");
            String grNumber = extractField(eventPayload, "grNumber");

            Money totalAmount = Money.of(
                new BigDecimal(totalAmountStr != null ? totalAmountStr : "0"),
                Currency.getInstance(currencyCode != null ? currencyCode : "USD")
            );

            // Trigger validation
            validationService.startValidation(
                invoiceId,
                invoiceNumber != null ? invoiceNumber : "UNKNOWN",
                totalAmount,
                poNumber,
                grNumber
            );

            logger.info("Successfully started validation for invoice: {}", invoiceId);

        } finally {
            TenantContext.clear();
        }
    }

    private void markEventProcessed(UUID eventId, Connection conn) throws SQLException {
        String insert = """
            INSERT INTO common.event_subscriptions (event_id, subscription_name, processed_at, status)
            VALUES (?, ?, CURRENT_TIMESTAMP, 'PROCESSED')
            """;

        try (PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setObject(1, eventId);
            stmt.setString(2, SUBSCRIPTION_NAME);
            stmt.executeUpdate();
        }
    }

    private void markEventFailed(UUID eventId, String errorMessage, Connection conn) throws SQLException {
        String insert = """
            INSERT INTO common.event_subscriptions (event_id, subscription_name, processed_at, status, error_message)
            VALUES (?, ?, CURRENT_TIMESTAMP, 'FAILED', ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setObject(1, eventId);
            stmt.setString(2, SUBSCRIPTION_NAME);
            stmt.setString(3, errorMessage);
            stmt.executeUpdate();
        }
    }

    /**
     * Simplified field extraction from JSON payload.
     * In production: Use Jackson ObjectMapper for proper JSON parsing.
     */
    private String extractField(String jsonPayload, String fieldName) {
        // Simplified extraction - in production use Jackson
        String searchKey = "\"" + fieldName + "\":";
        int startIdx = jsonPayload.indexOf(searchKey);
        if (startIdx == -1) return null;

        startIdx += searchKey.length();
        int valueStart = jsonPayload.indexOf("\"", startIdx);
        if (valueStart == -1) return null;

        int valueEnd = jsonPayload.indexOf("\"", valueStart + 1);
        if (valueEnd == -1) return null;

        return jsonPayload.substring(valueStart + 1, valueEnd);
    }
}
