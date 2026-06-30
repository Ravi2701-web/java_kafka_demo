package com.ecom.dto;

public class Events {

    // Consumed
    public record InventoryReservedEvent(
            String orderId,
            String productId,
            int quantity,
            boolean reserved
    ) {}

    // Published
    public record PaymentProcessedEvent(
            String orderId,
            String status,
            String reason
    ) {}
}
