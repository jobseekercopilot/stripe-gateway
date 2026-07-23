package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.config.StripeProperties;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import com.jobseekercopilot.stripegateway.exception.StripeConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "external-provider", name = "mode", havingValue = "LIVE", matchIfMissing = true)
public class StripeApiClient implements StripeProviderClient {
    private final RestClient stripeRestClient;
    private final StripeProperties stripeProperties;

    @Override
    public StripeCheckoutSession createCheckoutSession(
            CreateCheckoutSessionRequest request,
            String productName) {
        requireStripeSecret();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", stripeProperties.getSuccessUrl());
        form.add("cancel_url", stripeProperties.getCancelUrl());
        form.add("line_items[0][price_data][currency]", "gbp");
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(request.getPriceGbpPence()));
        form.add("line_items[0][price_data][product_data][name]", productName);
        form.add("line_items[0][quantity]", "1");
        form.add("metadata[userId]", request.getUserId());
        form.add("metadata[pricingPlanId]", request.getPricingPlanId());
        form.add("metadata[tokenAmount]", String.valueOf(request.getTokenAmount()));

        return stripeRestClient.post()
                .uri("/v1/checkout/sessions")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBearerAuth(stripeProperties.getSecretKey()))
                .body(form)
                .retrieve()
                .body(StripeCheckoutSession.class);
    }

    private void requireStripeSecret() {
        if (stripeProperties.getSecretKey() == null || stripeProperties.getSecretKey().isBlank()) {
            throw new StripeConfigurationException("STRIPE_SECRET_KEY is required");
        }
    }
}
