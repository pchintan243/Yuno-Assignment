package com.yuno.payment_orchestrator.enumtype;

/**
 * Represents the lifecycle state of a payment.
 * <p>
 * State transitions:
 *   INITIATED → PROCESSING → SUCCESS
 *                          ↘ FAILED
 */
public enum PaymentStatus {
    /** Payment record created but not yet submitted to a provider. */
    INITIATED,
    /** Payment submitted to a provider and awaiting or receiving a response. */
    PROCESSING,
    /** Payment accepted and confirmed by the provider. */
    SUCCESS,
    /** Payment rejected or failed after exhausting all retry/failover attempts. */
    FAILED
}