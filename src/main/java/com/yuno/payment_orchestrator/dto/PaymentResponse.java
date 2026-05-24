package com.yuno.payment_orchestrator.dto;

import com.yuno.payment_orchestrator.enumtype.PaymentStatus;
import com.yuno.payment_orchestrator.enumtype.PaymentType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    /** Unique identifier assigned to this payment by the system. */
    private String paymentId;

    /** The payment amount, matching the original request. */
    private Double amount;

    /** The payment type (CARD, UPI) used for this payment. */
    private PaymentType type;

    /** The current state of the payment: INITIATED, PROCESSING, SUCCESS, or FAILED. */
    private PaymentStatus status;

    /** The provider that processed this payment (ProviderA or ProviderB). */
    private String provider;
}