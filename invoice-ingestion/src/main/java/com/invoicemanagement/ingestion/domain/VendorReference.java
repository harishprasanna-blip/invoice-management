package com.invoicemanagement.ingestion.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing vendor identification information.
 * Immutable reference to vendor master data.
 *
 * DDD Pattern: Value Object
 * Bounded Context: InvoiceIngestionContext
 */
@Getter
@EqualsAndHashCode
@ToString
public class VendorReference implements Serializable {

    private final String vendorId;
    private final String vendorName;
    private final String taxId;

    @JsonCreator
    public VendorReference(
        @JsonProperty("vendorId") String vendorId,
        @JsonProperty("vendorName") String vendorName,
        @JsonProperty("taxId") String taxId
    ) {
        this.vendorId = Objects.requireNonNull(vendorId, "Vendor ID cannot be null");
        this.vendorName = Objects.requireNonNull(vendorName, "Vendor name cannot be null");
        this.taxId = taxId; // Optional - may not be present on all invoices
    }

    public static VendorReference of(String vendorId, String vendorName) {
        return new VendorReference(vendorId, vendorName, null);
    }

    public static VendorReference of(String vendorId, String vendorName, String taxId) {
        return new VendorReference(vendorId, vendorName, taxId);
    }

    /**
     * Check if tax ID is present.
     */
    public boolean hasTaxId() {
        return taxId != null && !taxId.isBlank();
    }
}
