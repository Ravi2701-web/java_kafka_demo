package com.ecom.consumer;

import com.ecom.dto.Events.InventoryReservedEvent;
import com.ecom.dto.Events.PaymentProcessedEvent;
import com.ecom.model.OrderStatus;
import com.ecom.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusConsumer {

    private final OrderRepository orderRepository;
    private final MeterRegistry meterRegistry;

    // ── Listen: inventory reserved or failed ──────────────────────────────────
    @KafkaListener(topics = "inventory-reserved", groupId = "order-service-group")
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Inventory reserved for orderId={}. Awaiting payment.", event.orderId());

        orderRepository.findByOrderId(event.orderId()).ifPresent(order -> {
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            orderRepository.save(order);
        });
    }

    @KafkaListener(topics = "inventory-failed", groupId = "order-service-group")
    public void handleInventoryFailed(InventoryReservedEvent event) {
        log.warn("Inventory FAILED for orderId={}. Marking order as FAILED.", event.orderId());

        orderRepository.findByOrderId(event.orderId()).ifPresent(order -> {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
        });

        meterRegistry.counter("orders.failed.total", "reason", "inventory_unavailable").increment();
    }

    // ── Listen: payment outcome ───────────────────────────────────────────────
    @KafkaListener(topics = "payment-processed", groupId = "order-service-group")
    public void handlePaymentProcessed(PaymentProcessedEvent event) {

        OrderStatus newStatus = "SUCCESS".equals(event.status())
                ? OrderStatus.CONFIRMED
                : OrderStatus.FAILED;

        log.info("Payment {} for orderId={}. Reason: {}",
                event.status(), event.orderId(), event.reason());

        orderRepository.findByOrderId(event.orderId()).ifPresent(order -> {
            order.setStatus(newStatus);
            orderRepository.save(order);
        });

        if ("SUCCESS".equals(event.status())) {
            meterRegistry.counter("orders.confirmed.total").increment();
        } else {
            meterRegistry.counter("orders.failed.total", "reason", "payment_declined").increment();
        }
    }
}
