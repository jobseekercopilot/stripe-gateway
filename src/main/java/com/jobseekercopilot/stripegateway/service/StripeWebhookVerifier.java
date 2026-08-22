package com.jobseekercopilot.stripegateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.stripegateway.config.ExternalProviderMode;
import com.jobseekercopilot.stripegateway.config.ExternalProviderProperties;
import com.jobseekercopilot.stripegateway.config.FixtureProperties;
import com.jobseekercopilot.stripegateway.config.StripeProperties;
import com.jobseekercopilot.stripegateway.exception.BadRequestException;
import com.jobseekercopilot.stripegateway.exception.StripeConfigurationException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StripeWebhookVerifier {
    private static final long DEFAULT_TOLERANCE_SECONDS = 300;
    private final StripeProperties stripeProperties;
    private final ExternalProviderProperties providerProperties;
    private final FixtureProperties fixtureProperties;
    private final ObjectMapper objectMapper;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public StripeWebhookVerifier(
            StripeProperties stripeProperties,
            ExternalProviderProperties providerProperties,
            FixtureProperties fixtureProperties,
            ObjectMapper objectMapper) {
        this.stripeProperties = stripeProperties;
        this.providerProperties = providerProperties;
        this.fixtureProperties = fixtureProperties;
        this.objectMapper = objectMapper;
    }

    StripeWebhookVerifier(StripeProperties stripeProperties, ObjectMapper objectMapper) {
        this(stripeProperties, new ExternalProviderProperties(), new FixtureProperties(), objectMapper);
    }

    public JsonNode verifyAndParse(String payload, String signatureHeader) {
        String webhookSecret = requireWebhookSecret();
        String timestamp = headerValue(signatureHeader, "t");
        String expectedSignature = hmacSha256(timestamp + "." + payload, webhookSecret);
        boolean verified = false;
        for (String candidate : headerValues(signatureHeader, "v1")) {
            // Do not stop at the first match: rotation headers can contain
            // multiple v1 values and each candidate gets the same comparison.
            verified |= constantTimeEquals(expectedSignature, candidate);
        }
        if (!verified) {
            log.warn("Stripe webhook verification failed reason=InvalidSignature");
            throw new BadRequestException("Invalid Stripe webhook signature");
        }
        verifyTimestamp(timestamp);
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new BadRequestException("Invalid Stripe webhook payload");
        }
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private String requireWebhookSecret() {
        String secret = providerProperties.getMode() == ExternalProviderMode.FIXTURE
                ? fixtureProperties.getWebhookSecret()
                : stripeProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new StripeConfigurationException(providerProperties.getMode() == ExternalProviderMode.FIXTURE
                    ? "STRIPE_FIXTURE_WEBHOOK_SECRET is required"
                    : "STRIPE_WEBHOOK_SECRET is required");
        }
        return secret;
    }

    private String headerValue(String header, String key) {
        return headerValues(header, key).get(0);
    }

    private List<String> headerValues(String header, String key) {
        if (header == null || header.isBlank()) {
            throw new BadRequestException("Missing Stripe signature header");
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] pieces = part.split("=", 2);
            if (pieces.length == 2 && pieces[0].trim().equals(key)) {
                values.add(pieces[1].trim());
            }
        }
        if (values.isEmpty()) {
            throw new BadRequestException("Missing Stripe signature " + key + " value");
        }
        return List.copyOf(values);
    }

    private void verifyTimestamp(String timestamp) {
        long signedAt;
        try {
            signedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            log.warn("Stripe webhook verification failed reason=InvalidTimestamp");
            throw new BadRequestException("Invalid Stripe signature timestamp");
        }
        long now = Instant.now(clock).getEpochSecond();
        if (Math.abs(now - signedAt) > DEFAULT_TOLERANCE_SECONDS) {
            log.warn("Stripe webhook verification failed reason=TimestampOutsideTolerance");
            throw new BadRequestException("Stripe webhook signature timestamp is outside tolerance");
        }
    }

    private String hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new BadRequestException("Unable to verify Stripe webhook signature");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null || expected.length() != actual.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expected.length(); i++) {
            result |= expected.charAt(i) ^ actual.charAt(i);
        }
        return result == 0;
    }
}
