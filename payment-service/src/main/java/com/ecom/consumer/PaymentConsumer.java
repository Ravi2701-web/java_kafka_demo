package com.ecom.consumer;

import com.ecom.dto.Events.InventoryReservedEvent;
import com.ecom.dto.Events.PaymentProcessedEvent;
import com.ecom.model.Payment;
import com.ecom.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentRepository paymentRepository;
    private final MeterRegistry meterRegistry;

    @Value("${payment.success-rate:0.8}")
    private double successRate;

    @KafkaListener(topics = "inventory-reserved", groupId = "payment-service-group")
    public void handleInventoryReserved(InventoryReservedEvent event) {

        log.info("Processing payment. orderId={} productId={} qty={}",
                event.orderId(), event.productId(), event.quantity());

        // ── IDEMPOTENCY GUARD ────────────────────────────────────────────────
        // If Kafka retries this message after a crash, we skip reprocessing.
        // Without this, the customer could be charged twice.
        if (paymentRepository.existsByOrderId(event.orderId())) {
            log.warn("Duplicate payment event detected — skipping. orderId={}", event.orderId());
            return;
        }

        // ── Simulate payment gateway (Razorpay / Stripe) ─────────────────────
        boolean paymentSuccess = Math.random() < successRate;
        String status = paymentSuccess ? "SUCCESS" : "FAILED";
        String reason = paymentSuccess ? null : "Payment gateway declined — insufficient funds";

        // ── Persist payment record ────────────────────────────────────────────
        Payment payment = Payment.builder()
                .orderId(event.orderId())
                .status(status)
                .reason(reason)
                .build();

        paymentRepository.save(payment);

        // ── Publish outcome ───────────────────────────────────────────────────
        kafkaTemplate.send("payment-processed", event.orderId(),
                new PaymentProcessedEvent(event.orderId(), status, reason));

        // ── Business metrics ──────────────────────────────────────────────────
        if (paymentSuccess) {
            meterRegistry.counter("payment.success.total").increment();
            log.info("✅ Payment SUCCESS. orderId={}", event.orderId());
        } else {
            meterRegistry.counter("payment.failed.total", "reason", "gateway_declined").increment();
            log.warn("❌ Payment FAILED. orderId={} reason={}", event.orderId(), reason);
        }
    }
}
