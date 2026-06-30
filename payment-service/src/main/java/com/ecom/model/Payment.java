package com.ecom.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderId;

    private String status;   // SUCCESS or FAILED
    private String reason;   // null on success

    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();
}
