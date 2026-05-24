package com.yuno.payment_orchestrator.orchestrator;

import com.yuno.payment_orchestrator.entity.Payment;
import com.yuno.payment_orchestrator.enumtype.PaymentStatus;
import com.yuno.payment_orchestrator.enumtype.PaymentType;
import com.yuno.payment_orchestrator.provider.PaymentProvider;
import com.yuno.payment_orchestrator.repository.PaymentRepository;
import com.yuno.payment_orchestrator.routing.PaymentRoutingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentOrchestratorRetryTest {

    @Mock
    private PaymentRoutingService routingService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProvider primaryProvider;

    @Mock
    private PaymentProvider fallbackProvider;

    private MeterRegistry meterRegistry;
    private PaymentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orchestrator = new PaymentOrchestrator(routingService, paymentRepository, meterRegistry);
    }

    // TC_I_03 — Retry mechanism: primary succeeds on first try
    @Test
    void shouldSucceedOnFirstProviderAttempt() {
        when(routingService.route(PaymentType.CARD)).thenReturn(primaryProvider);
        when(primaryProvider.processPayment(any())).thenReturn(true);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = Payment.builder()
                .id("pay-001")
                .type(PaymentType.CARD)
                .amount(100.0)
                .build();

        Payment result = orchestrator.process(payment);

        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        verify(primaryProvider, times(1)).processPayment(any());
        verify(fallbackProvider, never()).processPayment(any());
    }

    // TC_I_03 — Retry mechanism: primary fails, retries up to 3 times, then succeeds
    @Test
    void shouldRetryOnProviderFailureAndSucceed() {
        when(routingService.route(PaymentType.CARD)).thenReturn(primaryProvider);
        // Fail twice, succeed on third attempt
        when(primaryProvider.processPayment(any()))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = Payment.builder()
                .id("pay-002")
                .type(PaymentType.CARD)
                .amount(200.0)
                .build();

        Payment result = orchestrator.process(payment);

        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        // 3 total attempts (2 failures + 1 success)
        verify(primaryProvider, times(3)).processPayment(any());
        verify(fallbackProvider, never()).processPayment(any());
    }

    // TC_I_03 — Retry mechanism: primary fails all 3 attempts, triggers failover
    @Test
    void shouldExhaustRetriesAndTriggerFailover() {
        when(routingService.route(PaymentType.CARD)).thenReturn(primaryProvider);
        when(routingService.getFallback(PaymentType.CARD)).thenReturn(fallbackProvider);
        when(primaryProvider.processPayment(any())).thenReturn(false);
        when(fallbackProvider.processPayment(any())).thenReturn(true);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = Payment.builder()
                .id("pay-003")
                .type(PaymentType.CARD)
                .amount(300.0)
                .build();

        Payment result = orchestrator.process(payment);

        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        // 3 attempts on primary, 1 on fallback
        verify(primaryProvider, times(3)).processPayment(any());
        verify(fallbackProvider, times(1)).processPayment(any());
    }

    // TC_I_04 — Failover: primary fails, fallback succeeds
    @Test
    void shouldFailoverToSecondaryProviderWhenPrimaryFails() {
        when(routingService.route(PaymentType.UPI)).thenReturn(primaryProvider);
        when(routingService.getFallback(PaymentType.UPI)).thenReturn(fallbackProvider);
        when(fallbackProvider.getProviderName()).thenReturn("ProviderB");
        when(primaryProvider.processPayment(any())).thenReturn(false);
        when(fallbackProvider.processPayment(any())).thenReturn(true);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = Payment.builder()
                .id("pay-004")
                .type(PaymentType.UPI)
                .amount(400.0)
                .build();

        Payment result = orchestrator.process(payment);

        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        assertEquals("ProviderB", result.getProvider());
    }

    // TC_I_05 — Status transition: INITIATED → PROCESSING → SUCCESS
    @Test
    void shouldTransitionThroughCorrectStatusStates() {
        when(routingService.route(PaymentType.CARD)).thenReturn(primaryProvider);
        when(primaryProvider.processPayment(any())).thenReturn(true);

        // Capture the status at each save call
        java.util.List<PaymentStatus> capturedStatuses = new java.util.ArrayList<>();
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            capturedStatuses.add(p.getStatus());
            return p;
        });

        Payment payment = Payment.builder()
                .id("pay-005")
                .type(PaymentType.CARD)
                .amount(500.0)
                .build();

        Payment result = orchestrator.process(payment);

        // 2 saves: first sets PROCESSING, second sets SUCCESS
        assertEquals(2, capturedStatuses.size());
        assertEquals(PaymentStatus.PROCESSING, capturedStatuses.get(0));
        assertEquals(PaymentStatus.SUCCESS, capturedStatuses.get(1));
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
    }

    // TC_E_03 — Provider always fails → final status = FAILED
    @Test
    void shouldReturnFailedWhenBothProvidersFail() {
        when(routingService.route(PaymentType.CARD)).thenReturn(primaryProvider);
        when(routingService.getFallback(PaymentType.CARD)).thenReturn(fallbackProvider);
        when(primaryProvider.processPayment(any())).thenReturn(false);
        when(fallbackProvider.processPayment(any())).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = Payment.builder()
                .id("pay-006")
                .type(PaymentType.CARD)
                .amount(600.0)
                .build();

        Payment result = orchestrator.process(payment);

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        // 3 attempts on primary + 3 attempts on fallback = 6 total
        verify(primaryProvider, times(3)).processPayment(any());
        verify(fallbackProvider, times(3)).processPayment(any());
    }

    // TC_R_03 — Payment persistence: data remains consistent
    @Test
    void shouldPersistCorrectProviderInPaymentRecord() {
        when(routingService.route(PaymentType.CARD)).thenReturn(primaryProvider);
        when(primaryProvider.getProviderName()).thenReturn("ProviderA");
        when(primaryProvider.processPayment(any())).thenReturn(true);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = Payment.builder()
                .id("pay-007")
                .type(PaymentType.CARD)
                .amount(700.0)
                .build();

        Payment result = orchestrator.process(payment);

        assertEquals("ProviderA", result.getProvider());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
    }
}
