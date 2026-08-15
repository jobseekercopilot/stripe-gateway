package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.config.FixtureProperties;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.generated.systemdataservice.api.FixtureControllerApi;
import com.jobseekercopilot.generated.systemdataservice.model.FixtureStripeRequest;
import com.jobseekercopilot.generated.systemdataservice.model.FixtureStripeResponse;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external-provider", name = "mode", havingValue = "FIXTURE")
public class FixtureStripeProviderClient implements StripeProviderClient {
    private static final Logger log = LoggerFactory.getLogger(FixtureStripeProviderClient.class);
    private final FixtureProperties fixtureProperties;
    private final FixtureControllerApi fixtureControllerApi;

    public FixtureStripeProviderClient(FixtureProperties fixtureProperties, FixtureControllerApi fixtureControllerApi) {
        this.fixtureProperties = fixtureProperties;
        this.fixtureControllerApi = fixtureControllerApi;
    }

    @Override
    public StripeCheckoutSession createCheckoutSession(CreateCheckoutSessionRequest request, String productName) {
        FixtureStripeRequest payload = new FixtureStripeRequest()
                .datasetId(fixtureProperties.getDatasetId())
                .datasetVersion(fixtureProperties.getDatasetVersion())
                .scenario(fixtureProperties.getScenario())
                .operation("create-checkout-session")
                .userId(request.getUserId())
                .pricingPlanId(request.getPricingPlanId())
                .tokenAmount(request.getTokenAmount())
                .priceGbpPence(request.getPriceGbpPence());
        FixtureStripeResponse body = fixtureControllerApi.stripe(payload);
        StripeCheckoutSession session = new StripeCheckoutSession();
        session.setId(text(body == null ? null : body.getSessionId(), "cs_test_demo_fixture"));
        session.setUrl(uriText(
                body == null ? null : body.getCheckoutUrl(),
                "https://fixtures.jobseekercopilot.local/stripe/checkout"));
        session.setPaymentIntent(text(body == null ? null : body.getPaymentIntentId(), "pi_demo_fixture"));
        log.info("Stripe fixture checkout session created sessionId={} datasetId={} scenario={}",
                session.getId(), fixtureProperties.getDatasetId(), fixtureProperties.getScenario());
        return session;
    }

    @Override
    public StripeCheckoutSession createOwnedCheckoutSession(PaymentOrderSnapshot order) {
        FixtureStripeRequest payload = new FixtureStripeRequest()
                .datasetId(fixtureProperties.getDatasetId())
                .datasetVersion(fixtureProperties.getDatasetVersion())
                .scenario(fixtureProperties.getScenario())
                .operation("create-owned-checkout-session")
                .userId(order.getOwnerId())
                .pricingPlanId(order.getPricingPlanId())
                .tokenAmount((long) order.getDocumentCredits())
                .priceGbpPence(order.getPriceMinor());
        FixtureStripeResponse body = fixtureControllerApi.stripe(payload);
        StripeCheckoutSession session = new StripeCheckoutSession();
        session.setId(text(body == null ? null : body.getSessionId(), "cs_test_owned_fixture"));
        session.setUrl(uriText(
                body == null ? null : body.getCheckoutUrl(),
                "https://fixtures.jobseekercopilot.local/stripe/checkout"));
        session.setPaymentIntent(text(
                body == null ? null : body.getPaymentIntentId(), "pi_owned_fixture"));
        return session;
    }

    @Override
    public StripeCheckoutSession retrieveOwnedCheckoutSession(String sessionId) {
        StripeCheckoutSession session = new StripeCheckoutSession();
        session.setId(sessionId);
        session.setUrl("https://fixtures.jobseekercopilot.local/stripe/checkout/" + sessionId);
        session.setStatus("open");
        return session;
    }

    @Override
    public StripeCheckoutSession expireOwnedCheckoutSession(String sessionId) {
        StripeCheckoutSession session = retrieveOwnedCheckoutSession(sessionId);
        session.setStatus("expired");
        return session;
    }

    private String text(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String uriText(URI value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
