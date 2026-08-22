package com.jobseekercopilot.stripegateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore;
import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore.FixtureTerminalEvent;
import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore.FixtureTerminalSnapshot;
import com.jobseekercopilot.stripegateway.config.FixtureProperties;
import com.jobseekercopilot.stripegateway.exception.StripeConfigurationException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "external-provider", name = "mode", havingValue = "FIXTURE")
public class FixturePaymentControlService {
    private final FixtureStripeSessionStore sessionStore;
    private final FixtureProperties fixtureProperties;
    private final StripeGatewayService stripeGatewayService;
    private final ObjectMapper objectMapper;

    public FixturePaymentEventResponse emit(
            String sessionId, FixtureTerminalEvent event) {
        FixtureTerminalSnapshot terminal = sessionStore.terminal(sessionId, event);
        String payload = payload(terminal);
        long timestamp = java.time.Instant.now().getEpochSecond();
        String signature = "t=" + timestamp + ",v1="
                + hmac(timestamp + "." + payload, fixtureProperties.getWebhookSecret());
        // This is deliberately the normal signed webhook entry point. Fixture
        // control never calls Payment Service or grants credits directly.
        stripeGatewayService.handleWebhook(payload, signature);
        return new FixturePaymentEventResponse(
                terminal.eventId(),
                terminal.order().getOrderId().toString(),
                terminal.session().getId(),
                terminal.event().name(),
                terminal.session().getStatus().toUpperCase(),
                terminal.session().getPaymentStatus().toUpperCase());
    }

    private String payload(FixtureTerminalSnapshot terminal) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("id", terminal.eventId());
        event.put("type", terminal.event() == FixtureTerminalEvent.COMPLETED
                ? "checkout.session.completed" : "checkout.session.expired");
        event.put("livemode", false);
        event.put("created", terminal.eventCreatedAt().getEpochSecond());
        ObjectNode object = event.putObject("data").putObject("object");
        object.put("id", terminal.session().getId());
        object.put("payment_intent", terminal.session().getPaymentIntent());
        object.put("payment_status", terminal.session().getPaymentStatus());
        object.put("status", terminal.session().getStatus());
        object.put("currency", terminal.order().getCurrency().toLowerCase());
        object.put("amount_total", terminal.order().getPriceMinor());
        object.put("client_reference_id", terminal.order().getOrderId().toString());
        object.putObject("metadata")
                .put("orderId", terminal.order().getOrderId().toString());
        object.putObject("customer_details")
                .putObject("address")
                .put("country", terminal.order().getBillingCountry());
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new StripeConfigurationException(
                    "Fixture payment event could not be encoded");
        }
    }

    private String hmac(String value, String secret) {
        if (secret == null || secret.length() < 32) {
            throw new StripeConfigurationException(
                    "STRIPE_FIXTURE_WEBHOOK_SECRET must contain at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new StripeConfigurationException(
                    "Fixture payment event could not be signed");
        }
    }

    public record FixturePaymentEventResponse(
            String providerEventId,
            String orderId,
            String providerSessionId,
            String event,
            String checkoutStatus,
            String paymentStatus) {}
}
