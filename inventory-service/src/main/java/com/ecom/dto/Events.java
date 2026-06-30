package com.ecom.dto;

public class Events {

    // Consumed
    public record OrderPlacedEvent(
            String orderId,
            String customerId,
            String productId,
            int quantity,
            double totalAmount
    ) {}

    // Published
    public record InventoryReservedEvent(
            String orderId,
            String productId,
            int quantity,
            boolean reserved
    ) {}
}
