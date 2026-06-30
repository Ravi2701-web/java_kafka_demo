package com.ecom.dto;

// This is the response shape that inventory-service sends back
// AND that order-service's Feign client deserializes into InventoryResponse
// Both sides must have identical field names and types
public record InventoryResponse(
        String productId,
        String productName,
        int availableQty,
        int reservedQty,
        boolean exists,
        String message
) {}
