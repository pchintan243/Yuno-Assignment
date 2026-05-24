package com.yuno.payment_orchestrator.service;

import com.yuno.payment_orchestrator.dto.PaymentRequest;
import com.yuno.payment_orchestrator.entity.Payment;
import com.yuno.payment_orchestrator.enumtype.PaymentStatus;
import com.yuno.payment_orchestrator.enumtype.PaymentType;
import com.yuno.payment_orchestrator.orchestrator.PaymentOrchestrator;
import com.yuno.payment_orchestrator.repository.PaymentRepository;
import com.yuno.payment_orchestrator.service.impl.PaymentServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PaymentServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentOrchestrator orchestrator = mock(PaymentOrchestrator.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final PaymentServiceImpl service =
            new PaymentServiceImpl(paymentRepository, orchestrator, meterRegistry);

    // ✅ 1. Happy Path
    @Test
    void shouldCreatePaymentSuccessfully() {

        PaymentRequest request = PaymentRequest.builder()
                .amount(100.0)
                .type(PaymentType.CARD)
                .idempotencyKey("abc123")
                .build();

        Payment saved = Payment.builder()
                .id("1")
                .amount(100.0)
                .type(PaymentType.CARD)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderA")
                .build();

        when(paymentRepository.findByIdempotencyKey("abc123"))
                .thenReturn(Optional.empty());

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(saved);

        when(orchestrator.process(any(Payment.class)))
                .thenReturn(saved);

        var response = service.createPayment(request);

        assertEquals("1", response.getPaymentId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
    }

    // ✅ 2. Idempotency Test
    @Test
    void shouldReturnExistingPaymentForSameIdempotencyKey() {

        Payment existing = Payment.builder()
                .id("1")
                .amount(100.0)
                .type(PaymentType.CARD)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderA")
                .idempotencyKey("abc123")
                .build();

        when(paymentRepository.findByIdempotencyKey("abc123"))
                .thenReturn(Optional.of(existing));

        PaymentRequest request = PaymentRequest.builder()
                .amount(100.0)
                .type(PaymentType.CARD)
                .idempotencyKey("abc123")
                .build();

        var response = service.createPayment(request);

        verify(orchestrator, never()).process(any());
        assertEquals("1", response.getPaymentId());
    }

    // ✅ 3. Fetch Payment
    @Test
    void shouldFetchPaymentById() {

        Payment payment = Payment.builder()
                .id("1")
                .amount(200.0)
                .type(PaymentType.UPI)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderB")
                .build();

        when(paymentRepository.findById("1"))
                .thenReturn(Optional.of(payment));

        var response = service.getPayment("1");

        assertEquals("1", response.getPaymentId());
        assertEquals(PaymentType.UPI, response.getType());
    }

    // TC_R_02 — Duplicate key: idempotency check returns existing, no new payment created
    @Test
    void shouldNotCreateNewPaymentOnDuplicateIdempotencyKey() {
        Payment existing = Payment.builder()
                .id("existing-id")
                .amount(50.0)
                .type(PaymentType.CARD)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderA")
                .idempotencyKey("dup-key")
                .build();

        when(paymentRepository.findByIdempotencyKey("dup-key"))
                .thenReturn(Optional.of(existing));

        PaymentRequest request = PaymentRequest.builder()
                .amount(999.0) // different amount — should be ignored
                .type(PaymentType.UPI) // different type — should be ignored
                .idempotencyKey("dup-key")
                .build();

        var response = service.createPayment(request);

        // Orchestrator should never be called
        verify(orchestrator, never()).process(any());
        // Repository save should never be called for a duplicate
        verify(paymentRepository, never()).save(any());
        // Returns the existing payment
        assertEquals("existing-id", response.getPaymentId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
    }

    // TC_R_03 — Payment persistence: data remains consistent after create → fetch
    @Test
    void shouldPersistAndRetrievePaymentWithCorrectData() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(350.0)
                .type(PaymentType.UPI)
                .idempotencyKey("persist-key")
                .build();

        Payment savedPayment = Payment.builder()
                .id("persist-001")
                .amount(350.0)
                .type(PaymentType.UPI)
                .status(PaymentStatus.INITIATED)
                .idempotencyKey("persist-key")
                .build();

        Payment processedPayment = Payment.builder()
                .id("persist-001")
                .amount(350.0)
                .type(PaymentType.UPI)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderB")
                .idempotencyKey("persist-key")
                .build();

        when(paymentRepository.findByIdempotencyKey("persist-key"))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(savedPayment)
                .thenReturn(processedPayment);
        when(orchestrator.process(any(Payment.class)))
                .thenReturn(processedPayment);

        var response = service.createPayment(request);

        assertEquals("persist-001", response.getPaymentId());
        assertEquals(350.0, response.getAmount());
        assertEquals(PaymentType.UPI, response.getType());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals("ProviderB", response.getProvider());
    }

    // TC_N_06 — Fetch non-existent payment throws PaymentNotFoundException
    @Test
    void shouldThrowWhenPaymentNotFound() {
        when(paymentRepository.findById("non-existent"))
                .thenReturn(Optional.empty());

        assertThrows(
                com.yuno.payment_orchestrator.exception.PaymentNotFoundException.class,
                () -> service.getPayment("non-existent")
        );
    }

    // TC_E_01 — Very large amount is persisted correctly
    @Test
    void shouldHandleVeryLargeAmount() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(1_000_000.0)
                .type(PaymentType.CARD)
                .idempotencyKey("large-key")
                .build();

        Payment processed = Payment.builder()
                .id("large-001")
                .amount(1_000_000.0)
                .type(PaymentType.CARD)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderA")
                .build();

        when(paymentRepository.findByIdempotencyKey("large-key"))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(processed);
        when(orchestrator.process(any(Payment.class)))
                .thenReturn(processed);

        var response = service.createPayment(request);

        assertEquals(1_000_000.0, response.getAmount());
    }
}