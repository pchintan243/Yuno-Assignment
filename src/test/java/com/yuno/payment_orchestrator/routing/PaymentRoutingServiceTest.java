package com.yuno.payment_orchestrator.routing;

import com.yuno.payment_orchestrator.enumtype.PaymentType;
import com.yuno.payment_orchestrator.provider.PaymentProvider;
import com.yuno.payment_orchestrator.provider.impl.ProviderA;
import com.yuno.payment_orchestrator.provider.impl.ProviderB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PaymentRoutingServiceTest {

    @Mock
    private ProviderA providerA;

    @Mock
    private ProviderB providerB;

    private PaymentRoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new PaymentRoutingService(providerA, providerB);
    }

    // TC_I_01 — Route CARD → ProviderA
    @Test
    void shouldRouteCardToProviderA() {
        PaymentProvider provider = routingService.route(PaymentType.CARD);
        assertEquals(providerA, provider);
    }

    // TC_I_02 — Route UPI → ProviderB
    @Test
    void shouldRouteUpiToProviderB() {
        PaymentProvider provider = routingService.route(PaymentType.UPI);
        assertEquals(providerB, provider);
    }

    // TC_I_04 — Failover: CARD → fallback is ProviderB
    @Test
    void shouldFallbackToProviderBForCard() {
        PaymentProvider fallback = routingService.getFallback(PaymentType.CARD);
        assertEquals(providerB, fallback);
    }

    // TC_I_04 — Failover: UPI → fallback is ProviderA
    @Test
    void shouldFallbackToProviderAForUpi() {
        PaymentProvider fallback = routingService.getFallback(PaymentType.UPI);
        assertEquals(providerA, fallback);
    }

    // TC_N_07 — Unsupported payment type → exception
    // Note: Since PaymentType is an enum with only CARD and UPI, this test
    // documents the expected behavior if a new type is added without updating
    // the routing service. The test is omitted here as it would require
    // reflection to simulate an unknown enum value.
    // To add: create a new PaymentType enum value and verify routing throws.

}
