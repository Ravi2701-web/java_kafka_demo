package com.ecom.controller;

import com.ecom.client.InventoryClient;
import com.ecom.dto.Events.OrderPlacedEvent;
import com.ecom.dto.Events.PlaceOrderRequest;
import com.ecom.dto.InventoryResponse;
import com.ecom.model.Order;
import com.ecom.model.OrderStatus;
import com.ecom.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderRepository orderRepository;
    private final MeterRegistry meterRegistry;
    private final InventoryClient inventoryClient;  // ← Feign HTTP client (injected like any bean)

    // ── POST /api/orders ──────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody PlaceOrderRequest request) {

        // ════════════════════════════════════════════════════════════════════
        // STEP 1: SYNCHRONOUS check — does this product even exist?
        //
        // Why sync here? Because we need the answer RIGHT NOW before doing
        // anything else. We can't publish to Kafka and then find out it's
        // a fake productId — we'd have already saved a PENDING order for nothing.
        //
        // This is the hybrid pattern real systems use:
        //   - Sync (Feign HTTP) for validation checks that need an immediate answer
        //   - Async (Kafka) for the actual processing pipeline
        // ════════════════════════════════════════════════════════════════════
        log.info("Checking product existence via inventory-service. productId={}", request.productId());

        InventoryResponse inventoryCheck = inventoryClient.checkProduct(request.productId());

        // Product does not exist (or inventory-service is down → fallback returned exists=false)
        if (!inventoryCheck.exists()) {
            log.warn("Product check FAILED. productId={} reason={}",
                    request.productId(), inventoryCheck.message());

            meterRegistry.counter("orders.rejected.total", "reason", "product_not_found").increment();

            // Return 404 immediately — no DB write, no Kafka event, no wasted resources
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Product not found: " + request.productId()
                          + (inventoryCheck.message() != null ? " — " + inventoryCheck.message() : ""));
        }

        log.info("Product exists. productId={} productName={} availableQty={}",
                inventoryCheck.productId(), inventoryCheck.productName(), inventoryCheck.availableQty());

        // ════════════════════════════════════════════════════════════════════
        // STEP 2: ASYNC saga — product confirmed, now process the order
        // ════════════════════════════════════════════════════════════════════

        // 2a. Persist order as PENDING
        Order order = Order.builder()
                .orderId(UUID.randomUUID().toString())
                .customerId(request.customerId())
                .productId(request.productId())
                .quantity(request.quantity())
                .totalAmount(request.totalAmount())
                .status(OrderStatus.PENDING)
                .build();

        orderRepository.save(order);

        // 2b. Publish to Kafka — triggers the rest of the saga
        OrderPlacedEvent event = new OrderPlacedEvent(
                order.getOrderId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalAmount()
        );

        kafkaTemplate.send("order-placed", order.getOrderId(), event);

        // 2c. Custom business metric
        meterRegistry.counter("orders.placed.total",
                "product", request.productId(),
                "customer", request.customerId()).increment();

        log.info("Order placed and saga started. orderId={} productId={} amount={}",
                order.getOrderId(), order.getProductId(), order.getTotalAmount());

        return ResponseEntity.accepted().body(order);  // 202 Accepted
    }

    // ── GET /api/orders/{orderId} ─────────────────────────────────────────────
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        return orderRepository.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET /api/orders ───────────────────────────────────────────────────────
    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}
