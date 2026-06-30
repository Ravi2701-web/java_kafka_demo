package com.ecom.client;

import com.ecom.dto.InventoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// ── What is a Fallback? ───────────────────────────────────────────────────────
// If inventory-service is DOWN, times out, or throws 5xx errors repeatedly,
// Resilience4j "opens the circuit" — it stops calling the broken service
// and calls this fallback instead immediately.
//
// Why? Without a circuit breaker:
//   - Every order request would hang for 30s waiting for inventory-service
//   - Thread pool fills up → order-service also crashes → cascading failure
//
// With circuit breaker:
//   - After 5 failures, circuit opens
//   - Fallback is called instantly (no waiting)
//   - Every 30s, one "probe" request goes through to check if service recovered
//   - If recovered → circuit closes → normal operation resumes
//
// This is the difference between "one service down" and "entire platform down"
// ─────────────────────────────────────────────────────────────────────────────
@Component
@Slf4j
public class InventoryClientFallback implements InventoryClient {

    @Override
    public InventoryResponse checkProduct(String productId) {
        // We don't know if the product exists — safest default is to REJECT the order
        // rather than accidentally accept an order for a product that doesn't exist
        log.error("⚡ CIRCUIT OPEN: inventory-service is unreachable. " +
                  "Rejecting order for productId={}. Will retry when service recovers.", productId);

        // Return a "not found" response so OrderController returns 503 to the client
        return new InventoryResponse(productId, null, 0, 0, false, "inventory-service unavailable");
    }
}
