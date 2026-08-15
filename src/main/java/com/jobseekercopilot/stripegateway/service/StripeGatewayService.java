package com.jobseekercopilot.stripegateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobseekercopilot.stripegateway.client.PaymentServiceClient;
import com.jobseekercopilot.stripegateway.client.StripeProviderClient;
import com.jobseekercopilot.stripegateway.config.StripeProperties;
import com.jobseekercopilot.stripegateway.dto.ConfirmStripePurchaseRequest;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.dto.CreateOwnedCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.CreateOwnedCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.dto.ExpireOwnedCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.ExpireOwnedCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.stripegateway.dto.ProviderPaymentEventRequest;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import com.jobseekercopilot.stripegateway.exception.BadRequestException;
import com.jobseekercopilot.stripegateway.exception.StripeConfigurationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeGatewayService {
    private final StripeProviderClient stripeApiClient;
    private final StripeWebhookVerifier webhookVerifier;
    private final PaymentServiceClient paymentServiceClient;
    private final StripeProperties stripeProperties;

    public CreateCheckoutSessionResponse createCheckoutSession(
            String authenticatedOwner,
            CreateCheckoutSessionRequest request) {
        if (!stripeProperties.isLegacyCheckoutEnabled()) {
            throw new StripeConfigurationException(
                    "Legacy caller-priced Stripe Checkout is disabled");
        }
        long startedAt = System.nanoTime();
        if (!authenticatedOwner.equals(request.getUserId())) {
            throw new BadRequestException("Payment owner does not match authenticated context");
        }
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

    public CreateOwnedCheckoutSessionResponse createOwnedCheckoutSession(
            String authenticatedOwner,
            String idempotencyKey,
            CreateOwnedCheckoutSessionRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key is required");
        }
        PaymentOrderSnapshot order = paymentServiceClient.order(
                authenticatedOwner, request.getOrderId());
        if (order == null
                || order.getOrderId() == null
                || !request.getOrderId().equals(order.getOrderId())
                || !authenticatedOwner.equals(order.getOwnerId())
                || !"GBP".equals(order.getCurrency())
                || !"GB".equals(order.getBillingCountry())
                || !validTaxSnapshot(order)
                || order.getLegalEntityType() == null
                || "NOT_CONFIGURED".equals(order.getLegalEntityType())
                || order.getLegalEntityConfigurationVersion() == null
                || order.getLegalEntityConfigurationVersion().isBlank()
                || !order.isDisplayedPriceIsCheckoutTotal()) {
            throw new BadRequestException("Owned payment order is not valid for Checkout");
        }
        boolean replay = "CHECKOUT_OPEN".equals(order.getStatus())
                && order.getStripeSessionId() != null
                && !order.getStripeSessionId().isBlank();
        if (!replay && !"PENDING_CHECKOUT".equals(order.getStatus())) {
            throw new BadRequestException("Owned payment order is not open for Checkout");
        }
        StripeCheckoutSession session = replay
                ? stripeApiClient.retrieveOwnedCheckoutSession(order.getStripeSessionId())
                : stripeApiClient.createOwnedCheckoutSession(order);
        if (session == null || session.getId() == null || session.getId().isBlank()
                || session.getUrl() == null || session.getUrl().isBlank()) {
            throw new BadRequestException("Stripe Checkout returned an incomplete session");
        }
        PaymentOrderSnapshot bound = replay ? order : paymentServiceClient.bindCheckoutSession(
                authenticatedOwner, order.getOrderId(), session.getId());
        if (bound == null
                || !"CHECKOUT_OPEN".equals(bound.getStatus())
                || !session.getId().equals(bound.getStripeSessionId())) {
            throw new BadRequestException("Stripe Checkout session could not be bound to its order");
        }
        return CreateOwnedCheckoutSessionResponse.builder()
                .orderId(order.getOrderId())
                .sessionId(session.getId())
                .url(session.getUrl())
                .expiresAt(order.getExpiresAt())
                .promotionBonusDocumentCredits(order.getPromotionBonusDocumentCredits())
                .promotionGuaranteed(order.isPromotionGuaranteed())
                .build();
    }

    private boolean validTaxSnapshot(PaymentOrderSnapshot order) {
        return ("NOT_VAT_REGISTERED".equals(order.getTaxStatus())
                        && "VAT_NOT_CHARGED".equals(order.getTaxTreatment()))
                || ("VAT_REGISTERED".equals(order.getTaxStatus())
                        && "VAT_INCLUDED".equals(order.getTaxTreatment()));
    }

    public ExpireOwnedCheckoutSessionResponse expireOwnedCheckoutSession(
            String authenticatedOwner,
            ExpireOwnedCheckoutSessionRequest request) {
        PaymentOrderSnapshot order = paymentServiceClient.order(
                authenticatedOwner, request.orderId());
        if (order == null
                || !request.orderId().equals(order.getOrderId())
                || !authenticatedOwner.equals(order.getOwnerId())
                || !"CANCELLED".equals(order.getStatus())
                || !request.providerSessionId().equals(order.getStripeSessionId())) {
            throw new BadRequestException(
                    "Cancelled owned payment order does not match the Checkout session");
        }
        StripeCheckoutSession expired = stripeApiClient.expireOwnedCheckoutSession(
                request.providerSessionId());
        if (expired == null || !request.providerSessionId().equals(expired.getId())
                || !"expired".equals(expired.getStatus())) {
            throw new BadRequestException("Stripe Checkout session was not confirmed expired");
        }
        return new ExpireOwnedCheckoutSessionResponse(
                order.getOrderId(), expired.getId(), "EXPIRED");
    }

    public void handleWebhook(String payload, String signatureHeader) {
        long startedAt = System.nanoTime();
        log.info("Stripe webhook received hasSignature={} payloadBytes={}",
                signatureHeader != null && !signatureHeader.isBlank(),
                payload == null ? 0 : payload.length());
        JsonNode event = webhookVerifier.verifyAndParse(payload, signatureHeader);
        String eventType = text(event, "/type");
        log.info("Stripe webhook verified type={}", eventType);
        if (!supportedEvent(eventType)) {
            log.info("Stripe webhook ignored type={} durationMs={}",
                    eventType,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return;
        }
        JsonNode object = event.at("/data/object");
        String orderIdText = firstText(
                text(object, "/metadata/orderId"),
                text(object, "/client_reference_id"));
        if (orderIdText == null && "checkout.session.completed".equals(eventType)
                && object.path("metadata").hasNonNull("userId")) {
            forwardLegacyCheckout(object);
            return;
        }
        String providerEventId = requiredText(event, "id");
        ProviderPaymentEventRequest request = providerEvent(
                event, object, eventType, providerEventId, payload, orderIdText);
        paymentServiceClient.providerEvent(request);
        log.info("Stripe provider event forwarded type={} durationMs={}",
                eventType, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private ProviderPaymentEventRequest providerEvent(
            JsonNode event,
            JsonNode object,
            String eventType,
            String eventId,
            String payload,
            String orderIdText) {
        boolean checkout = eventType.startsWith("checkout.session.");
        return ProviderPaymentEventRequest.builder()
                .providerEventId(eventId)
                .eventType(eventType)
                .payloadSha256(sha256(payload))
                .orderId(uuid(orderIdText))
                .stripeSessionId(checkout ? text(object, "/id") : null)
                .paymentIntentId(text(object, "/payment_intent"))
                .paymentStatus(checkout ? text(object, "/payment_status") : null)
                .checkoutStatus(checkout ? text(object, "/status") : null)
                .currency(text(object, "/currency"))
                .amountTotalMinor(checkout ? nullableLong(object, "amount_total") : null)
                .billingCountry(checkout
                        ? text(object, "/customer_details/address/country") : null)
                .liveMode(event.path("livemode").asBoolean(false))
                .eventCreatedAt(event.hasNonNull("created")
                        ? Instant.ofEpochSecond(event.path("created").asLong()) : Instant.now())
                .reversalAmountMinor("charge.refunded".equals(eventType)
                        ? nullableLong(object, "amount_refunded")
                        : "charge.dispute.created".equals(eventType)
                        ? nullableLong(object, "amount") : null)
                .build();
    }

    private void forwardLegacyCheckout(JsonNode session) {
        JsonNode metadata = session.path("metadata");
        paymentServiceClient.confirmStripePurchase(ConfirmStripePurchaseRequest.builder()
                .userId(requiredText(metadata, "userId"))
                .pricingPlanId(requiredText(metadata, "pricingPlanId"))
                .tokenAmount(requiredLong(metadata, "tokenAmount"))
                .stripeSessionId(requiredText(session, "id"))
                .stripePaymentIntentId(text(session, "/payment_intent"))
                .build());
    }

    private boolean supportedEvent(String type) {
        return "checkout.session.completed".equals(type)
                || "checkout.session.expired".equals(type)
                || "charge.refunded".equals(type)
                || "charge.dispute.created".equals(type);
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.longValue() : null;
    }

    private UUID uuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("Stripe event has invalid orderId");
        }
    }

    private String firstText(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
