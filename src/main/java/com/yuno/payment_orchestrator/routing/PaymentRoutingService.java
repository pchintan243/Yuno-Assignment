package com.yuno.payment_orchestrator.routing;

import com.yuno.payment_orchestrator.enumtype.PaymentType;
import com.yuno.payment_orchestrator.exception.UnsupportedPaymentTypeException;
import com.yuno.payment_orchestrator.provider.PaymentProvider;
import com.yuno.payment_orchestrator.provider.impl.ProviderA;
import com.yuno.payment_orchestrator.provider.impl.ProviderB;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRoutingService {

    private final ProviderA providerA;
    private final ProviderB providerB;

    /**
     * Returns the primary (preferred) provider for the given payment type.
     * The selection is deterministic and based solely on the payment type.
     *
     * @param type the type of payment (CARD, UPI, etc.)
     * @return the primary provider to use
     * @throws UnsupportedPaymentTypeException if the type is not recognized
     */
    public PaymentProvider route(PaymentType type) {
        log.info("Routing payment type: {}", type);
        return switch (type) {
            case CARD -> {
                log.debug("Selected ProviderA for CARD payments");
                yield providerA;
            }
            case UPI -> {
                log.debug("Selected ProviderB for UPI payments");
                yield providerB;
            }
            default -> throw new UnsupportedPaymentTypeException("Unsupported payment type: " + type);
        };
    }

    /**
     * Returns the fallback provider to use when the primary provider fails.
     * For each payment type, the fallback is the provider NOT selected as primary.
     *
     * @param type the type of payment
     * @return the fallback provider
     * @throws UnsupportedPaymentTypeException if the type is not recognized
     */
    public PaymentProvider getFallback(PaymentType type) {
        log.info("Selecting fallback provider for type: {}", type);
        return switch (type) {
            case CARD -> {
                log.debug("Fallback ProviderB for CARD payments");
                yield providerB;
            }
            case UPI -> {
                log.debug("Fallback ProviderA for UPI payments");
                yield providerA;
            }
            default -> throw new UnsupportedPaymentTypeException("Unsupported payment type: " + type);
        };
    }
}