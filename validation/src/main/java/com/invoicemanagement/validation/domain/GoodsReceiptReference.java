package com.invoicemanagement.validation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Value Object representing Goods Receipt reference from SAP.
 * Translated from SAP via Anti-Corruption Layer (ACL).
 *
 * DDD Pattern: Value Object
 * Bounded Context: ValidationContext
 * Source: SAP ERP (via ACL)
 */
@Getter
@EqualsAndHashCode
@ToString
public class GoodsReceiptReference implements Serializable {

    private final String grNumber;
    private final String poNumber;
    private final LocalDate receivedDate;
    private final String receivedBy;
    private final List<GRLineItem> grLineItems;
    private final Map<String, BigDecimal> receivedQuantities; // lineNumber -> quantity

    @JsonCreator
    public GoodsReceiptReference(
        @JsonProperty("grNumber") String grNumber,
        @JsonProperty("poNumber") String poNumber,
        @JsonProperty("receivedDate") LocalDate receivedDate,
        @JsonProperty("receivedBy") String receivedBy,
        @JsonProperty("grLineItems") List<GRLineItem> grLineItems,
        @JsonProperty("receivedQuantities") Map<String, BigDecimal> receivedQuantities
    ) {
        this.grNumber = Objects.requireNonNull(grNumber, "GR number cannot be null");
        this.poNumber = Objects.requireNonNull(poNumber, "PO number cannot be null");
        this.receivedDate = Objects.requireNonNull(receivedDate, "Received date cannot be null");
        this.receivedBy = receivedBy;
        this.grLineItems = grLineItems != null ? List.copyOf(grLineItems) : Collections.emptyList();
        this.receivedQuantities = receivedQuantities != null ? Map.copyOf(receivedQuantities) : Collections.emptyMap();
    }

    public static GoodsReceiptReference of(
        String grNumber,
        String poNumber,
        LocalDate receivedDate,
        List<GRLineItem> lineItems
    ) {
        // Calculate received quantities map
        Map<String, BigDecimal> quantities = lineItems.stream()
            .collect(java.util.stream.Collectors.toMap(
                GRLineItem::getLineNumber,
                GRLineItem::getReceivedQuantity
            ));

        return new GoodsReceiptReference(
            grNumber,
            poNumber,
            receivedDate,
            null,
            lineItems,
            quantities
        );
    }

    /**
     * Get received quantity for a specific line.
     */
    public BigDecimal getReceivedQuantity(String lineNumber) {
        return receivedQuantities.getOrDefault(lineNumber, BigDecimal.ZERO);
    }

    /**
     * Get immutable copy of line items.
     */
    public List<GRLineItem> getGrLineItems() {
        return Collections.unmodifiableList(grLineItems);
    }

    /**
     * GR line item value object.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class GRLineItem implements Serializable {
        private final String lineNumber;
        private final String materialId;
        private final BigDecimal receivedQuantity;
        private final String storageLocation;

        @JsonCreator
        public GRLineItem(
            @JsonProperty("lineNumber") String lineNumber,
            @JsonProperty("materialId") String materialId,
            @JsonProperty("receivedQuantity") BigDecimal receivedQuantity,
            @JsonProperty("storageLocation") String storageLocation
        ) {
            this.lineNumber = Objects.requireNonNull(lineNumber, "Line number cannot be null");
            this.materialId = materialId;
            this.receivedQuantity = Objects.requireNonNull(receivedQuantity, "Received quantity cannot be null");
            this.storageLocation = storageLocation;
        }
    }
}
