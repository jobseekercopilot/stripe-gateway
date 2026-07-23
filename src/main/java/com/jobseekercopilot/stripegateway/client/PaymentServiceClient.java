package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.dto.ConfirmStripePurchaseRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentServiceClient {
    private final RestClient paymentServiceRestClient;

    public PaymentServiceClient(@Qualifier("paymentServiceRestClient") RestClient paymentServiceRestClient) {
        this.paymentServiceRestClient = paymentServiceRestClient;
    }

    public void confirmStripePurchase(ConfirmStripePurchaseRequest request) {
        paymentServiceRestClient.post()
                .uri("/api/v1/payments/confirm-stripe-purchase")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
