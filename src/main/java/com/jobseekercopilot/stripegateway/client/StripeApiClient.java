package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.config.StripeProperties;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.stripegateway.exception.StripeConfigurationException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
        if (!stripeProperties.isLegacyCheckoutEnabled()) {
            throw new StripeConfigurationException("Legacy caller-priced Stripe Checkout is disabled");
        }
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

    @Override
    public StripeCheckoutSession createOwnedCheckoutSession(PaymentOrderSnapshot order) {
        requireStripeSecret();
        if (order == null || order.getOrderId() == null
                || !"PENDING_CHECKOUT".equals(order.getStatus())
                || order.getExpiresAt() == null) {
            throw new StripeConfigurationException("Owned payment order is not open for Checkout");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", returnUrl(stripeProperties.getSuccessUrl(), order));
        form.add("cancel_url", returnUrl(stripeProperties.getCancelUrl(), order));
        form.add("expires_at", String.valueOf(order.getExpiresAt().getEpochSecond()));
        form.add("billing_address_collection", "required");
        form.add("customer_creation", "always");
        form.add("payment_method_types[0]", "card");
        form.add("client_reference_id", order.getOrderId().toString());
        form.add("line_items[0][price_data][currency]", order.getCurrency().toLowerCase());
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(order.getPriceMinor()));
        form.add("line_items[0][price_data][product_data][name]",
                "Job Seeker Copilot - " + order.getPricingPlanName() + " document credits");
        form.add("line_items[0][quantity]", "1");
        form.add("metadata[orderId]", order.getOrderId().toString());
        form.add("metadata[catalogVersion]", order.getCatalogVersion());
        form.add("payment_intent_data[metadata][orderId]", order.getOrderId().toString());

        return stripeRestClient.post()
                .uri("/v1/checkout/sessions")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> {
                    headers.setBearerAuth(stripeProperties.getSecretKey());
                    headers.set("Stripe-Version", stripeProperties.getApiVersion());
                    headers.set("Idempotency-Key", "checkout:" + order.getOrderId());
                })
                .body(form)
                .retrieve()
                .body(StripeCheckoutSession.class);
    }

    @Override
    public StripeCheckoutSession retrieveOwnedCheckoutSession(String sessionId) {
        requireStripeSecret();
        if (sessionId == null || !sessionId.matches("cs_(test|live)_[A-Za-z0-9_]+")) {
            throw new StripeConfigurationException("Owned Checkout session ID is invalid");
        }
        return stripeRestClient.get()
                .uri("/v1/checkout/sessions/{sessionId}", sessionId)
                .headers(headers -> {
                    headers.setBearerAuth(stripeProperties.getSecretKey());
                    headers.set("Stripe-Version", stripeProperties.getApiVersion());
                })
                .retrieve()
                .body(StripeCheckoutSession.class);
    }

    @Override
    public StripeCheckoutSession expireOwnedCheckoutSession(String sessionId) {
        StripeCheckoutSession current = retrieveOwnedCheckoutSession(sessionId);
        if (current != null
                && ("expired".equals(current.getStatus())
                || "complete".equals(current.getStatus()))) {
            return current;
        }
        try {
            return stripeRestClient.post()
                    .uri("/v1/checkout/sessions/{sessionId}/expire", sessionId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(headers -> {
                        headers.setBearerAuth(stripeProperties.getSecretKey());
                        headers.set("Stripe-Version", stripeProperties.getApiVersion());
                        headers.set("Idempotency-Key", "expire:" + sessionId);
                    })
                    .body(new LinkedMultiValueMap<String, String>())
                    .retrieve()
                    .body(StripeCheckoutSession.class);
        } catch (RestClientException ambiguous) {
            StripeCheckoutSession reconciled = retrieveOwnedCheckoutSession(sessionId);
            if (reconciled != null
                    && ("expired".equals(reconciled.getStatus())
                    || "complete".equals(reconciled.getStatus()))) {
                return reconciled;
            }
            throw ambiguous;
        }
    }

    private void requireStripeSecret() {
        if (stripeProperties.getSecretKey() == null || stripeProperties.getSecretKey().isBlank()) {
            throw new StripeConfigurationException("STRIPE_SECRET_KEY is required");
        }
    }

    private String returnUrl(String base, PaymentOrderSnapshot order) {
        URI uri;
        try {
            uri = URI.create(base);
        } catch (RuntimeException invalid) {
            throw new StripeConfigurationException(
                    "Stripe Checkout return URL is invalid");
        }
        if (uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new StripeConfigurationException(
                    "Stripe Checkout return URLs may contain only the server-owned order reference");
        }
        return base + "?order_id=" + order.getOrderId();
    }
}
