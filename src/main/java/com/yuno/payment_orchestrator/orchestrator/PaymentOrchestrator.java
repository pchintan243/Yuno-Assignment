package com.yuno.payment_orchestrator.orchestrator;

import com.yuno.payment_orchestrator.entity.Payment;
import com.yuno.payment_orchestrator.enumtype.PaymentStatus;
import com.yuno.payment_orchestrator.provider.PaymentProvider;
import com.yuno.payment_orchestrator.repository.PaymentRepository;
import com.yuno.payment_orchestrator.routing.PaymentRoutingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Core execution engine for payment processing.
 * <p>
 * Implements a two-stage reliability strategy:
 * 1. Retry — attempt the primary provider up to MAX_RETRIES times
 * 2. Failover — if primary fails all retries, fall back to the secondary provider
 * <p>
 * Each stage saves the current payment state to the DB, providing a complete
 * audit trail of what happened at every step.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestrator {

    private final PaymentRoutingService routingService;
    private final PaymentRepository paymentRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Maximum retry attempts per provider before considering it failed.
     * Retry loop: attempts 0, 1, 2  (so attempts <= MAX_RETRIES means 3 total tries)
     */
    private static final int MAX_RETRIES = 2;

    /**
     * Processes a payment through routing → retry → failover.
     * Persists state after each significant step so the DB always reflects the
     * current position in the pipeline (useful for debugging and audit).
     */
    public Payment process(Payment payment) {
        PaymentProvider primary = routingService.route(payment.getType());

        payment.setProvider(primary.getProviderName());
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);
        log.info("Processing paymentId={} with primary={}", payment.getId(), primary.getProviderName());

        boolean success = attempt(primary, payment, "primary");

        if (!success) {
            PaymentProvider fallback = routingService.getFallback(payment.getType());
            log.warn("Primary provider failed for paymentId={}, switching to fallback={}",
                    payment.getId(), fallback.getProviderName());

            incrementCounter("payment.failover.triggered", payment.getType().name());
            payment.setProvider(fallback.getProviderName());
            paymentRepository.save(payment);

            success = attempt(fallback, payment, "fallback");
        }

        payment.setStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        return paymentRepository.save(payment);
    }

    /**
     * Executes up to (MAX_RETRIES + 1) attempts with the given provider.
     * Returns true on the first successful result; false only if all attempts fail.
     */
    private boolean attempt(PaymentProvider provider, Payment payment, String stage) {
        int attempts = 0;
        boolean success = false;

        while (attempts <= MAX_RETRIES && !success) {
            success = provider.processPayment(payment);
            attempts++;
            incrementCounter("payment.provider.attempt", payment.getType().name(), provider.getProviderName());
            log.debug("paymentId={} provider={} attempt={} result={}",
                    payment.getId(), provider.getProviderName(), attempts, success);
        }
        return success;
    }

    private void incrementCounter(String name, String... tags) {
        String type = tags.length > 0 && tags[0] != null ? tags[0] : "unknown";
        String provider = tags.length > 1 && tags[1] != null ? tags[1] : "unknown";
        meterRegistry.counter(name, "type", type, "provider", provider).increment();
    }
}
