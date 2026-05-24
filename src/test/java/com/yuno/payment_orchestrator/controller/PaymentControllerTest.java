package com.yuno.payment_orchestrator.controller;

import com.yuno.payment_orchestrator.dto.PaymentRequest;
import com.yuno.payment_orchestrator.dto.PaymentResponse;
import com.yuno.payment_orchestrator.enumtype.PaymentStatus;
import com.yuno.payment_orchestrator.enumtype.PaymentType;
import com.yuno.payment_orchestrator.exception.PaymentNotFoundException;
import com.yuno.payment_orchestrator.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(paymentService);
    }

    // TC_S_01 — Create payment (CARD) → SUCCESS
    @Test
    void shouldCreateCardPaymentSuccessfully() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(100.0)
                .type(PaymentType.CARD)
                .idempotencyKey("key-001")
                .build();

        PaymentResponse expectedResponse = PaymentResponse.builder()
                .paymentId("uuid-001")
                .amount(100.0)
                .type(PaymentType.CARD)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderA")
                .build();

        when(paymentService.createPayment(any())).thenReturn(expectedResponse);

        PaymentResponse result = controller.createPayment(request);

        assertEquals("uuid-001", result.getPaymentId());
        assertEquals(100.0, result.getAmount());
        assertEquals(PaymentType.CARD, result.getType());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        assertEquals("ProviderA", result.getProvider());
    }

    // TC_S_02 — Create payment (UPI) → ProviderB
    @Test
    void shouldCreateUpiPaymentSuccessfully() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(200.0)
                .type(PaymentType.UPI)
                .idempotencyKey("key-002")
                .build();

        PaymentResponse expectedResponse = PaymentResponse.builder()
                .paymentId("uuid-002")
                .amount(200.0)
                .type(PaymentType.UPI)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderB")
                .build();

        when(paymentService.createPayment(any())).thenReturn(expectedResponse);

        PaymentResponse result = controller.createPayment(request);

        assertEquals("uuid-002", result.getPaymentId());
        assertEquals(PaymentType.UPI, result.getType());
        assertEquals("ProviderB", result.getProvider());
    }

    // TC_S_03 — Fetch payment by ID
    @Test
    void shouldFetchPaymentById() {
        PaymentResponse expectedResponse = PaymentResponse.builder()
                .paymentId("uuid-001")
                .amount(100.0)
                .type(PaymentType.CARD)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderA")
                .build();

        when(paymentService.getPayment("uuid-001")).thenReturn(expectedResponse);

        PaymentResponse result = controller.getPayment("uuid-001");

        assertEquals("uuid-001", result.getPaymentId());
        assertEquals(100.0, result.getAmount());
        assertEquals(PaymentType.CARD, result.getType());
    }

    // TC_N_06 — Invalid payment ID (not found)
    @Test
    void shouldThrowWhenPaymentNotFound() {
        when(paymentService.getPayment("non-existent"))
                .thenThrow(new PaymentNotFoundException("Payment not found: non-existent"));

        assertThrows(PaymentNotFoundException.class, () -> controller.getPayment("non-existent"));
    }

    // TC_S_03 — List payments (pagination)
    @Test
    void shouldListPaymentsWithPagination() {
        PaymentResponse payment = PaymentResponse.builder()
                .paymentId("uuid-001")
                .amount(100.0)
                .type(PaymentType.CARD)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderA")
                .build();

        when(paymentService.listPayments(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment)));

        var result = controller.listPayments(Pageable.unpaged());

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    // TC_E_01 — Very large amount
    @Test
    void shouldProcessVeryLargeAmount() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(1_000_000.0)
                .type(PaymentType.CARD)
                .idempotencyKey("key-007")
                .build();

        PaymentResponse expectedResponse = PaymentResponse.builder()
                .paymentId("uuid-007")
                .amount(1_000_000.0)
                .type(PaymentType.CARD)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderA")
                .build();

        when(paymentService.createPayment(any())).thenReturn(expectedResponse);

        PaymentResponse result = controller.createPayment(request);

        assertEquals(1_000_000.0, result.getAmount());
    }

    // TC_E_02 — Rapid duplicate requests (same idempotency key, returns same response)
    @Test
    void shouldReturnSamePaymentForDuplicateIdempotencyKey() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(50.0)
                .type(PaymentType.UPI)
                .idempotencyKey("key-dup")
                .build();

        PaymentResponse expectedResponse = PaymentResponse.builder()
                .paymentId("uuid-dup")
                .amount(50.0)
                .type(PaymentType.UPI)
                .status(PaymentStatus.SUCCESS)
                .provider("ProviderB")
                .build();

        when(paymentService.createPayment(any())).thenReturn(expectedResponse);

        PaymentResponse result1 = controller.createPayment(request);
        PaymentResponse result2 = controller.createPayment(request);

        assertEquals("uuid-dup", result1.getPaymentId());
        assertEquals("uuid-dup", result2.getPaymentId());
    }

    // TC_E_03 — Payment fails (final status = FAILED)
    @Test
    void shouldReturnFailedStatusWhenPaymentFails() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(75.0)
                .type(PaymentType.CARD)
                .idempotencyKey("key-008")
                .build();

        PaymentResponse expectedResponse = PaymentResponse.builder()
                .paymentId("uuid-008")
                .amount(75.0)
                .type(PaymentType.CARD)
                .status(PaymentStatus.FAILED)
                .provider("ProviderB")
                .build();

        when(paymentService.createPayment(any())).thenReturn(expectedResponse);

        PaymentResponse result = controller.createPayment(request);

        assertEquals(PaymentStatus.FAILED, result.getStatus());
    }
}
