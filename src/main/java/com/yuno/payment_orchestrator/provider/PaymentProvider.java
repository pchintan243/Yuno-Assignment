package com.yuno.payment_orchestrator.provider;

import com.yuno.payment_orchestrator.entity.Payment;

/**
 * Abstraction over external payment providers (e.g., ProviderA, ProviderB).
 * Each provider implements this interface to integrate with the orchestrator.
 * Keeping a single interface makes it easy to add new providers or swap implementations.
 */
public interface PaymentProvider {

    /**
     * Submits a payment to the provider and returns whether it succeeded.
     *
     * @param payment the payment to process; must have a non-null id
     * @return true if the provider accepted the payment, false if it rejected/failed
     */
    boolean processPayment(Payment payment);

    /**
     * Returns a human-readable name for this provider, used in logs and audit trails.
     */
    String getProviderName();
}