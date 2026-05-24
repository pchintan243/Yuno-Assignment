package com.yuno.payment_orchestrator.orchestrator;

import com.yuno.payment_orchestrator.entity.Payment;
import com.yuno.payment_orchestrator.enumtype.PaymentType;
import com.yuno.payment_orchestrator.provider.PaymentProvider;
import com.yuno.payment_orchestrator.repository.PaymentRepository;
import com.yuno.payment_orchestrator.routing.PaymentRoutingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class PaymentOrchestratorTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void shouldFallbackWhenPrimaryFails() {

        PaymentRoutingService routingService = mock(PaymentRoutingService.class);
        PaymentRepository repo = mock(PaymentRepository.class);

        PaymentProvider primary = mock(PaymentProvider.class);
        PaymentProvider fallback = mock(PaymentProvider.class);

        when(routingService.route(PaymentType.CARD)).thenReturn(primary);
        when(routingService.getFallback(PaymentType.CARD)).thenReturn(fallback);

        when(primary.processPayment(any())).thenReturn(false);
        when(fallback.processPayment(any())).thenReturn(true);

        PaymentOrchestrator orchestrator =
                new PaymentOrchestrator(routingService, repo, meterRegistry);

        Payment payment = Payment.builder()
                .type(PaymentType.CARD)
                .build();

        orchestrator.process(payment);

        verify(primary, atLeastOnce()).processPayment(any());
        verify(fallback, atLeastOnce()).processPayment(any());
    }
}