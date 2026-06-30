package com.ecom.controller;

import com.ecom.dto.InventoryResponse;
import com.ecom.model.Inventory;
import com.ecom.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryRepository inventoryRepository;

    @GetMapping
    public List<Inventory> getAll() {
        return inventoryRepository.findAll();
    }

    // ── GET /api/inventory/{productId} ────────────────────────────────────────
    // Called by order-service via Feign before placing an order.
    // Returns InventoryResponse with exists=true/false instead of 404,
    // so Feign doesn't throw an exception on a "not found" — it just maps the body.
    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getByProduct(@PathVariable String productId) {

        return inventoryRepository.findByProductId(productId)
                .map(inv -> {
                    log.info("Product check: FOUND. productId={} name={} qty={}",
                            productId, inv.getProductName(), inv.getAvailableQty());

                    return ResponseEntity.ok(new InventoryResponse(
                            inv.getProductId(),
                            inv.getProductName(),
                            inv.getAvailableQty(),
                            inv.getReservedQty(),
                            true,   // ← exists = true
                            null
                    ));
                })
                .orElseGet(() -> {
                    log.warn("Product check: NOT FOUND. productId={}", productId);

                    // Still return 200 with exists=false — cleaner than 404 for Feign clients
                    // 404 would trigger Feign's error decoder and throw an exception in order-service
                    return ResponseEntity.ok(new InventoryResponse(
                            productId,
                            null,
                            0,
                            0,
                            false,  // ← exists = false
                            "Product does not exist in catalog"
                    ));
                });
    }

    // ── PUT /api/inventory/{productId}/stock ──────────────────────────────────
    // Use this to set stock=0 for testing the out-of-stock failure path
    @PutMapping("/{productId}/stock")
    public ResponseEntity<Inventory> updateStock(
            @PathVariable String productId,
            @RequestParam int qty) {

        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElse(Inventory.builder()
                        .productId(productId)
                        .productName("Product-" + productId)
                        .availableQty(qty)
                        .reservedQty(0)
                        .build());

        inv.setAvailableQty(qty);
        return ResponseEntity.ok(inventoryRepository.save(inv));
    }

    // ── POST /api/inventory/seed ──────────────────────────────────────────────
    @PostMapping("/seed")
    public String seed() {
        inventoryRepository.save(Inventory.builder()
                .productId("PROD-001").productName("MacBook Pro").availableQty(50).reservedQty(0).build());
        inventoryRepository.save(Inventory.builder()
                .productId("PROD-002").productName("iPhone 15").availableQty(200).reservedQty(0).build());
        inventoryRepository.save(Inventory.builder()
                .productId("PROD-003").productName("AirPods Pro").availableQty(5).reservedQty(0).build());
        return "Seeded 3 products: PROD-001 (MacBook), PROD-002 (iPhone), PROD-003 (AirPods)";
    }
}
