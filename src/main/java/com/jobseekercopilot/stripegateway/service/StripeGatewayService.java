package com.jobseekercopilot.stripegateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobseekercopilot.stripegateway.client.PaymentServiceClient;
import com.jobseekercopilot.stripegateway.client.StripeProviderClient;
import com.jobseekercopilot.stripegateway.dto.ConfirmStripePurchaseRequest;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import com.jobseekercopilot.stripegateway.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeGatewayService {
    private final StripeProviderClient stripeApiClient;
    private final StripeWebhookVerifier webhookVerifier;
    private final PaymentServiceClient paymentServiceClient;

    public CreateCheckoutSessionResponse createCheckoutSession(CreateCheckoutSessionRequest request) {
        long startedAt = System.nanoTime();
        log.info("Stripe checkout session creation started userId={} pricingPlanId={} tokenAmount={} priceGbpPence={}",
                request.getUserId(),
                request.getPricingPlanId(),
                request.getTokenAmount(),
                request.getPriceGbpPence());
        StripeCheckoutSession session = stripeApiClient.createCheckoutSession(request, productName(request));
        if (session == null || session.getId() == null || session.getUrl() == null) {
            throw new BadRequestException("Stripe checkout session response was incomplete");
        }
        log.info("Stripe checkout session created userId={} pricingPlanId={} sessionId={} durationMs={}",
                request.getUserId(),
                request.getPricingPlanId(),
                session.getId(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return CreateCheckoutSessionResponse.builder()
                .sessionId(session.getId())
                .checkoutUrl(session.getUrl())
                .build();
    }

    public void handleWebhook(String payload, String signatureHeader) {
        long startedAt = System.nanoTime();
        log.info("Stripe webhook received hasSignature={} payloadBytes={}",
                signatureHeader != null && !signatureHeader.isBlank(),
                payload == null ? 0 : payload.length());
        JsonNode event = webhookVerifier.verifyAndParse(payload, signatureHeader);
        String eventType = text(event, "/type");
        log.info("Stripe webhook verified type={}", eventType);
        if (!"checkout.session.completed".equals(eventType)) {
            log.info("Stripe webhook ignored type={} durationMs={}",
                    eventType,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return;
        }
        JsonNode session = event.at("/data/object");
        JsonNode metadata = session.path("metadata");
        String sessionId = requiredText(session, "id");
        paymentServiceClient.confirmStripePurchase(ConfirmStripePurchaseRequest.builder()
                .userId(requiredText(metadata, "userId"))
                .pricingPlanId(requiredText(metadata, "pricingPlanId"))
                .tokenAmount(requiredLong(metadata, "tokenAmount"))
                .stripeSessionId(sessionId)
                .stripePaymentIntentId(text(session, "/payment_intent"))
                .build());
        log.info("Stripe payment confirmation forwarded sessionId={} durationMs={}",
                sessionId,
                (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String productName(CreateCheckoutSessionRequest request) {
        return "Job Seeker Copilot AI Tokens - " + titleCase(request.getPricingPlanId());
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "Bundle";
        }
        String normalised = value.replace('-', ' ').replace('_', ' ');
        String[] words = normalised.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase());
            }
        }
        return result.isEmpty() ? "Bundle" : result.toString();
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Stripe checkout session missing " + field);
        }
        return value;
    }

    private long requiredLong(JsonNode node, String field) {
        String value = requiredText(node, field);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Stripe checkout session has invalid " + field);
        }
    }

    private String text(JsonNode node, String pointer) {
        JsonNode value = node.at(pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
