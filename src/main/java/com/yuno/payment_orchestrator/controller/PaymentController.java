package com.yuno.payment_orchestrator.controller;

import com.yuno.payment_orchestrator.dto.PaymentRequest;
import com.yuno.payment_orchestrator.dto.PaymentResponse;
import com.yuno.payment_orchestrator.exception.PaymentNotFoundException;
import com.yuno.payment_orchestrator.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * REST entry point for all payment operations.
 * All-endpoints delegate to {@link PaymentService} which handles
 * idempotency, orchestration, and persistence.
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Initiates a new payment. The request is validated, and if a payment with
     * the same idempotency key already exists, the existing record is returned
     * (idempotent replay). Otherwise, the payment is routed to a provider and
     * processed through the orchestrator.
     *
     * @param request payment details including amount, type, and idempotency key
     * @return the created (or existing) payment with its final status
     */
    @PostMapping
    public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest request) {
        return paymentService.createPayment(request);
    }

    /**
     * Retrieves a single payment by its unique identifier.
     *
     * @param id the UUID of the payment record
     * @return the payment details
     * @throws PaymentNotFoundException if no payment matches the given, id
     */
    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable String id) {
        return paymentService.getPayment(id);
    }

    /**
     * Returns a paginated list of all payments, ordered by creation time descending.
     *
     * @param pageable Spring pagination parameters (page, size, sort)
     * @return a page of payment responses
     */
    @GetMapping
    public Page<PaymentResponse> listPayments(
            @PageableDefault(size = 20) Pageable pageable) {
        return paymentService.listPayments(pageable);
    }
}