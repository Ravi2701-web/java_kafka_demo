package com.ecom.dto;

// ── Events published BY order-service ────────────────────────────────────────

public class Events {

    public record OrderPlacedEvent(
            String orderId,
            String customerId,
            String productId,
            int quantity,
            double totalAmount
    ) {}

    // ── Events CONSUMED by order-service ─────────────────────────────────────

    public record InventoryReservedEvent(
            String orderId,
            String productId,
            int quantity,
            boolean reserved  // true = reserved, false = failed
    ) {}

    public record PaymentProcessedEvent(
            String orderId,
            String status,   // "SUCCESS" or "FAILED"
            String reason    // null on success, error message on failure
    ) {}

    // ── REST request/response ─────────────────────────────────────────────────

    public record PlaceOrderRequest(
            String customerId,
            String productId,
            int quantity,
            double totalAmount
    ) {}
}
