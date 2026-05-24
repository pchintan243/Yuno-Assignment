package com.yuno.payment_orchestrator.entity;

import com.yuno.payment_orchestrator.enumtype.PaymentStatus;
import com.yuno.payment_orchestrator.enumtype.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private Double amount;

    @Enumerated(EnumType.STRING)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    /**
     * Identifies which payment provider (ProviderA or ProviderB) processed this payment.
     * Useful for auditing which provider handled each transaction.
     */
    private String provider;

    private String idempotencyKey;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}