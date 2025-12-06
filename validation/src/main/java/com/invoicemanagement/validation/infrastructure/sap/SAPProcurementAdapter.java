package com.invoicemanagement.validation.infrastructure.sap;

import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.validation.domain.GoodsReceiptReference;
import com.invoicemanagement.validation.domain.PurchaseOrderReference;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Anti-Corruption Layer (ACL) adapter for SAP ERP integration.
 * Translates SAP procurement data models to domain value objects.
 * Protects domain from SAP API changes.
 *
 * DDD Pattern: Anti-Corruption Layer
 * Bounded Context: ValidationContext
 * External System: SAP ERP
 */
@Component
public class SAPProcurementAdapter {

    private final WebClient sapWebClient;

    public SAPProcurementAdapter(WebClient sapWebClient) {
        this.sapWebClient = sapWebClient;
    }

    /**
     * Fetch Purchase Order from SAP and translate to domain value object.
     * Cached for 5 minutes to reduce SAP API calls.
     *
     * @param poNumber SAP Purchase Order number
     * @param tenantId Tenant identifier for routing
     * @return Optional of PurchaseOrderReference, empty if not found
     */
    @Cacheable(value = "purchaseOrders", key = "#tenantId + '_' + #poNumber")
    public Optional<PurchaseOrderReference> fetchPurchaseOrder(String poNumber, String tenantId) {
        try {
            SAPPurchaseOrder sapPo = sapWebClient
                .get()
                .uri("/api/procurement/purchase-orders/{poNumber}", poNumber)
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(SAPPurchaseOrder.class)
                .block();

            if (sapPo == null) {
                return Optional.empty();
            }

            // Translate SAP model to domain model
            PurchaseOrderReference poReference = translatePurchaseOrder(sapPo);
            return Optional.of(poReference);

        } catch (Exception e) {
            // Log error and return empty (ACL isolates domain from SAP failures)
            return Optional.empty();
        }
    }

    /**
     * Fetch Goods Receipt from SAP and translate to domain value object.
     * Cached for 5 minutes to reduce SAP API calls.
     *
     * @param grNumber SAP Goods Receipt number
     * @param tenantId Tenant identifier for routing
     * @return Optional of GoodsReceiptReference, empty if not found
     */
    @Cacheable(value = "goodsReceipts", key = "#tenantId + '_' + #grNumber")
    public Optional<GoodsReceiptReference> fetchGoodsReceipt(String grNumber, String tenantId) {
        try {
            SAPGoodsReceipt sapGr = sapWebClient
                .get()
                .uri("/api/procurement/goods-receipts/{grNumber}", grNumber)
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(SAPGoodsReceipt.class)
                .block();

            if (sapGr == null) {
                return Optional.empty();
            }

            // Translate SAP model to domain model
            GoodsReceiptReference grReference = translateGoodsReceipt(sapGr);
            return Optional.of(grReference);

        } catch (Exception e) {
            // Log error and return empty (ACL isolates domain from SAP failures)
            return Optional.empty();
        }
    }

    /**
     * Fetch Goods Receipt by PO number from SAP.
     *
     * @param poNumber Purchase Order number
     * @param tenantId Tenant identifier for routing
     * @return Optional of GoodsReceiptReference, empty if not found
     */
    @Cacheable(value = "goodsReceiptsByPo", key = "#tenantId + '_' + #poNumber")
    public Optional<GoodsReceiptReference> fetchGoodsReceiptByPO(String poNumber, String tenantId) {
        try {
            SAPGoodsReceipt sapGr = sapWebClient
                .get()
                .uri("/api/procurement/purchase-orders/{poNumber}/goods-receipts/latest", poNumber)
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(SAPGoodsReceipt.class)
                .block();

            if (sapGr == null) {
                return Optional.empty();
            }

            GoodsReceiptReference grReference = translateGoodsReceipt(sapGr);
            return Optional.of(grReference);

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Translate SAP Purchase Order to domain value object.
     * This is the core ACL translation logic.
     */
    private PurchaseOrderReference translatePurchaseOrder(SAPPurchaseOrder sapPo) {
        // Translate SAP status to domain status
        PurchaseOrderReference.POStatus poStatus = translatePOStatus(sapPo.getStatus());

        // Translate line items
        List<PurchaseOrderReference.POLineItem> lineItems = sapPo.getItems().stream()
            .map(this::translatePOLineItem)
            .collect(Collectors.toList());

        // Create domain value object
        Money poAmount = Money.of(
            new BigDecimal(sapPo.getTotalAmount()),
            Currency.getInstance(sapPo.getCurrency())
        );

        return new PurchaseOrderReference(
            sapPo.getPoNumber(),
            LocalDate.parse(sapPo.getPoDate()),
            sapPo.getVendorId(),
            sapPo.getCurrency(),
            poAmount,
            poStatus,
            lineItems
        );
    }

    /**
     * Translate SAP PO line item to domain value object.
     */
    private PurchaseOrderReference.POLineItem translatePOLineItem(SAPPurchaseOrder.Item sapItem) {
        Money unitPrice = Money.of(
            new BigDecimal(sapItem.getUnitPrice()),
            Currency.getInstance(sapItem.getCurrency())
        );

        Money lineAmount = Money.of(
            new BigDecimal(sapItem.getNetAmount()),
            Currency.getInstance(sapItem.getCurrency())
        );

        return new PurchaseOrderReference.POLineItem(
            sapItem.getItemNumber(),
            sapItem.getMaterialNumber(),
            sapItem.getShortText(),
            new BigDecimal(sapItem.getOrderQuantity()),
            unitPrice,
            lineAmount
        );
    }

    /**
     * Translate SAP PO status to domain status.
     */
    private PurchaseOrderReference.POStatus translatePOStatus(String sapStatus) {
        return switch (sapStatus) {
            case "OPEN", "RELEASED" -> PurchaseOrderReference.POStatus.OPEN;
            case "PART_BILLED" -> PurchaseOrderReference.POStatus.PARTIALLY_BILLED;
            case "FULLY_BILLED" -> PurchaseOrderReference.POStatus.FULLY_BILLED;
            case "CLOSED" -> PurchaseOrderReference.POStatus.CLOSED;
            case "CANCELLED", "DELETED" -> PurchaseOrderReference.POStatus.CANCELLED;
            default -> PurchaseOrderReference.POStatus.OPEN;
        };
    }

    /**
     * Translate SAP Goods Receipt to domain value object.
     */
    private GoodsReceiptReference translateGoodsReceipt(SAPGoodsReceipt sapGr) {
        // Translate line items
        List<GoodsReceiptReference.GRLineItem> lineItems = sapGr.getItems().stream()
            .map(this::translateGRLineItem)
            .collect(Collectors.toList());

        return new GoodsReceiptReference(
            sapGr.getMaterialDocument(),
            sapGr.getPurchaseOrder(),
            LocalDate.parse(sapGr.getPostingDate()),
            sapGr.getCreatedBy(),
            lineItems,
            null // receivedQuantities map is calculated in value object
        );
    }

    /**
     * Translate SAP GR line item to domain value object.
     */
    private GoodsReceiptReference.GRLineItem translateGRLineItem(SAPGoodsReceipt.Item sapItem) {
        return new GoodsReceiptReference.GRLineItem(
            sapItem.getMaterialDocumentItem(),
            sapItem.getMaterial(),
            new BigDecimal(sapItem.getQuantity()),
            sapItem.getStorageLocation()
        );
    }
}
