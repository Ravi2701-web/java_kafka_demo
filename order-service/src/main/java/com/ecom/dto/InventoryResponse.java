package com.ecom.dto;

// This matches the JSON shape returned by inventory-service's GET /api/inventory/{productId}
// Feign deserializes the HTTP response body into this record automatically
public record InventoryResponse(
        String productId,
        String productName,
        int availableQty,
        int reservedQty,
        boolean exists,      // false = product not found OR service unavailable
        String message       // human-readable reason (null on success)
) {}
