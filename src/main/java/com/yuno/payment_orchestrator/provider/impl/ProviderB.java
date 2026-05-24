package com.yuno.payment_orchestrator.provider.impl;

import com.yuno.payment_orchestrator.entity.Payment;
import com.yuno.payment_orchestrator.provider.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Payment provider implementation backed by ProviderB's API.
 * In production, this class would call the actual ProviderB HTTP endpoint.
 * The current implementation uses a random outcome for demonstration purposes.
 *
 * @see PaymentProvider
 */
@Component
@Slf4j
public class ProviderB implements PaymentProvider {

    private final Random random = new Random();

    /**
     * Processes a payment through ProviderB.
     *
     * @param payment the payment to process; must have a non-null id and amount
     * @return true if the payment succeeded, false otherwise
     */
    @Override
    public boolean processPayment(Payment payment) {
        boolean result = random.nextBoolean();
        log.info("ProviderB paymentId={} amount={} result={}",
                payment.getId(), payment.getAmount(), result);
        return result;
    }

    @Override
    public String getProviderName() {
        return "ProviderB";
    }
}
