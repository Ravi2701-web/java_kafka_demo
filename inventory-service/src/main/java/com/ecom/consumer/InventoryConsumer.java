package com.ecom.consumer;

import com.ecom.dto.Events.InventoryReservedEvent;
import com.ecom.dto.Events.OrderPlacedEvent;
import com.ecom.model.Inventory;
import com.ecom.repository.InventoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final InventoryRepository inventoryRepository;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "order-placed", groupId = "inventory-service-group")
    @Transactional  // ensures DB update + Kafka publish are atomic
    public void handleOrderPlaced(OrderPlacedEvent event) {

        log.info("Received ORDER_PLACED. orderId={} productId={} qty={}",
                event.orderId(), event.productId(), event.quantity());

        Inventory inventory = inventoryRepository.findByProductId(event.productId())
                .orElseGet(() -> {
                    // Auto-create inventory for demo products if not seeded
                    log.warn("Product not found, creating with 100 qty. productId={}", event.productId());
                    return Inventory.builder()
                            .productId(event.productId())
                            .productName("Product-" + event.productId())
                            .availableQty(100)
                            .reservedQty(0)
                            .build();
                });

        if (inventory.getAvailableQty() >= event.quantity()) {
            // ✅ Stock available — reserve it
            inventory.setAvailableQty(inventory.getAvailableQty() - event.quantity());
            inventory.setReservedQty(inventory.getReservedQty() + event.quantity());
            inventoryRepository.save(inventory);

            kafkaTemplate.send("inventory-reserved", event.orderId(),
                    new InventoryReservedEvent(event.orderId(), event.productId(), event.quantity(), true));

            meterRegistry.counter("inventory.reserved.total").increment();

            log.info("✅ Inventory reserved. orderId={} remaining={}",
                    event.orderId(), inventory.getAvailableQty());

        } else {
            // ❌ Out of stock — publish failure, saga rolls back
            kafkaTemplate.send("inventory-failed", event.orderId(),
                    new InventoryReservedEvent(event.orderId(), event.productId(), 0, false));

            meterRegistry.counter("inventory.failed.total", "reason", "out_of_stock").increment();

            log.warn("❌ Insufficient inventory. orderId={} available={} requested={}",
                    event.orderId(), inventory.getAvailableQty(), event.quantity());
        }
    }
}
