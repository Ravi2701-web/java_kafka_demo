package com.ecom.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class KafkaConfig {

    // ── Topic definitions ─────────────────────────────────────────────────────
    // Creating topics here ensures they exist before producers/consumers start

    @Bean public NewTopic orderPlacedTopic() {
        return TopicBuilder.name("order-placed").partitions(3).replicas(1).build();
    }

    @Bean public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name("inventory-reserved").partitions(3).replicas(1).build();
    }

    @Bean public NewTopic inventoryFailedTopic() {
        return TopicBuilder.name("inventory-failed").partitions(3).replicas(1).build();
    }

    @Bean public NewTopic paymentProcessedTopic() {
        return TopicBuilder.name("payment-processed").partitions(3).replicas(1).build();
    }

    // DLT = Dead Letter Topics (messages go here after max retries)
    @Bean public NewTopic orderPlacedDLT() {
        return TopicBuilder.name("order-placed.DLT").partitions(1).replicas(1).build();
    }

    // ── Error handler with retry + DLT ───────────────────────────────────────

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // After 3 attempts (1 original + 2 retries), send to DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // Route to topic.DLT, same partition as original
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".DLT", record.partition()
                )
        );

        // Wait 2 seconds between retries, retry twice
        FixedBackOff backOff = new FixedBackOff(2000L, 2L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
