package com.yuno.payment_orchestrator.enumtype;

/**
 * Supported payment instrument types.
 * <p>
 * Each type is routed to a specific primary provider:
 *   CARD → ProviderA
 *   UPI → ProviderB
 * <p>
 * Adding a new type here also requires updating {@link com.yuno.payment_orchestrator.routing.PaymentRoutingService}.
 */
public enum PaymentType {
    /** Credit or debit card payments. */
    CARD,
    /** Unified Payments Interface (India real-time payment method). */
    UPI
}