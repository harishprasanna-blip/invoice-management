package com.invoicemanagement.validation.infrastructure.sap;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * SAP Goods Receipt DTO (external system model).
 * This represents SAP's data model, not our domain model.
 * ACL translates this to GoodsReceiptReference.
 */
@Data
public class SAPGoodsReceipt {

    @JsonProperty("MaterialDocument")
    private String materialDocument;

    @JsonProperty("PurchaseOrder")
    private String purchaseOrder;

    @JsonProperty("PostingDate")
    private String postingDate;

    @JsonProperty("CreatedBy")
    private String createdBy;

    @JsonProperty("MaterialDocumentItem")
    private List<Item> items;

    @Data
    public static class Item {
        @JsonProperty("MaterialDocumentItem")
        private String materialDocumentItem;

        @JsonProperty("Material")
        private String material;

        @JsonProperty("QuantityInEntryUnit")
        private String quantity;

        @JsonProperty("StorageLocation")
        private String storageLocation;
    }
}
