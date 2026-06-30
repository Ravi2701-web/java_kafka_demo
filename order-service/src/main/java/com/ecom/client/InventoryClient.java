package com.ecom.client;

import com.ecom.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// ── What this does ────────────────────────────────────────────────────────────
// @FeignClient turns this interface into a real HTTP client automatically.
// You write the interface. Spring generates the implementation at startup.
// Calling inventoryClient.checkProduct("PROD-001") becomes:
//   GET http://inventory-service/api/inventory/PROD-001
// No RestTemplate, no WebClient, no manual URL building.
//
// name     = logical service name (used for circuit breaker naming)
// url      = actual address (in production this comes from service discovery like Eureka)
// fallback = what to call if inventory-service is DOWN (circuit breaker)
// ─────────────────────────────────────────────────────────────────────────────
@FeignClient(
        name = "inventory-service",
        url = "${services.inventory.url}",          // from application.yml
        fallback = InventoryClientFallback.class    // circuit breaker fallback
)
public interface InventoryClient {

    // Maps exactly to inventory-service's GET /api/inventory/{productId}
    @GetMapping("/api/inventory/{productId}")
    InventoryResponse checkProduct(@PathVariable("productId") String productId);
}
