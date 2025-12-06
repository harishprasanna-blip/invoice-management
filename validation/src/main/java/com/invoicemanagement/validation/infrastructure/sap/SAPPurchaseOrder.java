package com.invoicemanagement.validation.infrastructure.sap;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * SAP Purchase Order DTO (external system model).
 * This represents SAP's data model, not our domain model.
 * ACL translates this to PurchaseOrderReference.
 */
@Data
public class SAPPurchaseOrder {

    @JsonProperty("PurchaseOrder")
    private String poNumber;

    @JsonProperty("PurchaseOrderDate")
    private String poDate;

    @JsonProperty("Supplier")
    private String vendorId;

    @JsonProperty("DocumentCurrency")
    private String currency;

    @JsonProperty("TotalNetAmount")
    private String totalAmount;

    @JsonProperty("PurchaseOrderStatus")
    private String status;

    @JsonProperty("PurchaseOrderItem")
    private List<Item> items;

    @Data
    public static class Item {
        @JsonProperty("PurchaseOrderItem")
        private String itemNumber;

        @JsonProperty("Material")
        private String materialNumber;

        @JsonProperty("PurchaseOrderItemText")
        private String shortText;

        @JsonProperty("OrderQuantity")
        private String orderQuantity;

        @JsonProperty("NetPriceAmount")
        private String unitPrice;

        @JsonProperty("NetAmount")
        private String netAmount;

        @JsonProperty("DocumentCurrency")
        private String currency;
    }
}
