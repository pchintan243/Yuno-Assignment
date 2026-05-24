package com.yuno.payment_orchestrator.service.impl;

import com.yuno.payment_orchestrator.dto.PaymentRequest;
import com.yuno.payment_orchestrator.dto.PaymentResponse;
import com.yuno.payment_orchestrator.entity.Payment;
import com.yuno.payment_orchestrator.enumtype.PaymentStatus;
import com.yuno.payment_orchestrator.exception.PaymentNotFoundException;
import com.yuno.payment_orchestrator.orchestrator.PaymentOrchestrator;
import com.yuno.payment_orchestrator.repository.PaymentRepository;
import com.yuno.payment_orchestrator.service.PaymentService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentOrchestrator orchestrator;
    private final MeterRegistry meterRegistry;

    /**
     * Creates a new payment with idempotency protection.
     *
     * Flow:
     * 1. Check if a payment with the same idempotency key already exists — if so,
     *    return it without creating a duplicate (safe to retry on network failure).
     * 2. Build a new Payment entity in INITIATED state and persist it.
     * 3. Hand the payment to the orchestrator, which routes it to a provider,
     *    retries on failure, and may failover to a secondary provider.
     * 4. Return the final payment state.
     *
     * The entire flow is wrapped in a single transaction so that a crash between
     * steps does not leave partial state visible to callers.
     */
    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        log.info("Creating payment with idempotencyKey={}", request.getIdempotencyKey());

        // Idempotency check — return the existing payment if this key was seen before.
        Payment existing = paymentRepository
                .findByIdempotencyKey(request.getIdempotencyKey())
                .orElse(null);

        if (existing != null) {
            log.info("Idempotent request detected, returning existing paymentId={}", existing.getId());
            incrementCounter("payment.idempotent.hit", request.getType().name());
            return mapToResponse(existing);
        }

        incrementCounter("payment.idempotent.miss", request.getType().name());

        Payment payment = Payment.builder()
                .amount(request.getAmount())
                .type(request.getType())
                .status(PaymentStatus.INITIATED)
                .idempotencyKey(request.getIdempotencyKey())
                .createdAt(LocalDateTime.now())
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment initiated paymentId={}", payment.getId());

        Timer.Sample sample = Timer.start(meterRegistry);
        payment = orchestrator.process(payment);
        sample.stop(Timer.builder("payment.processing.duration")
                .description("End-to-end payment processing time")
                .tag("type", payment.getType().name())
                .tag("provider", payment.getProvider() != null ? payment.getProvider() : "unknown")
                .tag("status", payment.getStatus().name())
                .register(meterRegistry));

        log.info("Payment processed paymentId={} status={}", payment.getId(), payment.getStatus());

        incrementCounter("payment.created", payment.getType().name(), payment.getStatus().name());
        return mapToResponse(payment);
    }

    /**
     * Looks up a single payment by id. Uses a read-only transaction since no
     * data is being modified.
     *
     * @throws PaymentNotFoundException if the id does not match any payment
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + id));

        return mapToResponse(payment);
    }

    /**
     * Returns a paginated list of all payments. Read-only for performance.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Converts a Payment entity to a PaymentResponse DTO.
     * The DTO intentionally omits internal fields like idempotencyKey and timestamps.
     */
    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .type(payment.getType())
                .status(payment.getStatus())
                .provider(payment.getProvider())
                .build();
    }

    private void incrementCounter(String name, String... tags) {
        String type = tags.length > 0 && tags[0] != null ? tags[0] : "unknown";
        meterRegistry.counter(name, "type", type).increment();
    }
}
