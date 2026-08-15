package com.jobseekercopilot.stripegateway.exception;

public class StripeCheckoutSagaException extends RuntimeException {
    public StripeCheckoutSagaException(String message) {
        super(message);
    }

    public StripeCheckoutSagaException(String message, Throwable cause) {
        super(message, cause);
    }
}
