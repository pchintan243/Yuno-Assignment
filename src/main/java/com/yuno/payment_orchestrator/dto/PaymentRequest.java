package com.yuno.payment_orchestrator.dto;

import com.yuno.payment_orchestrator.enumtype.PaymentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    /**
     * The payment amount. Must be a positive number.
     * Validation is enforced via Jakarta Bean Validation annotations.
     */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private Double amount;

    /**
     * The payment method/type, e.g., CARD or UPI.
     * Determines which provider is selected for processing.
     */
    @NotNull(message = "Payment type is required")
    private PaymentType type;

    /**
     * Client-provided key to ensure idempotent request processing.
     * If the same key is submitted twice, the second request returns the
     * existing payment without creating a duplicate.
     */
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}