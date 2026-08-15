package com.jobseekercopilot.stripegateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderModeSafetyTest {

    @Test
    void productionCanStartDisabledWithExplicitSafeProviderConfiguration() {
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.DISABLED);
        StripeProperties stripe = stripeProperties();

        assertThatCode(() -> safety(provider, stripe, productionEnvironment()).run(
                        new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    void productionRejectsReturnUrlWithCallerControlledQuery() {
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.DISABLED);
        StripeProperties stripe = stripeProperties();
        stripe.setSuccessUrl("https://app.example.test/payment/success?owner=unsafe");

        assertThatThrownBy(() -> safety(provider, stripe, productionEnvironment()).run(
                        new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not include credentials, a query, or a fragment");
    }

    @Test
    void productionRejectsNonStripeApiOrigin() {
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.DISABLED);
        StripeProperties stripe = stripeProperties();
        stripe.setApiBaseUrl("https://attacker.example.test");

        assertThatThrownBy(() -> safety(provider, stripe, productionEnvironment()).run(
                        new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api.stripe.com");
    }

    @Test
    void productionRejectsLocalPaymentServiceUrl() {
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.DISABLED);
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("PAYMENT_SERVICE_URL", "http://localhost:8099");

        assertThatThrownBy(() -> safety(provider, stripeProperties(), environment).run(
                        new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-local HTTP(S) origin");
    }

    @Test
    void liveModeRequiresARealLiveKeyAndReleaseAuthorisation() {
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.LIVE);
        StripeProperties stripe = stripeProperties();
        stripe.setLiveReleaseAuthorised(true);
        stripe.setSecretKey("sk_test_not-live");

        assertThatThrownBy(() -> safety(provider, stripe, new MockEnvironment()).run(
                        new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("live secret key");
    }

    private ProviderModeSafety safety(
            ExternalProviderProperties provider,
            StripeProperties stripe,
            MockEnvironment environment) {
        return new ProviderModeSafety(
                provider, new FixtureProperties(), environment, stripe);
    }

    private StripeProperties stripeProperties() {
        StripeProperties stripe = new StripeProperties();
        stripe.setApiBaseUrl("https://api.stripe.com");
        stripe.setApiVersion("2026-02-25.clover");
        stripe.setSuccessUrl("https://app.example.test/payment/success");
        stripe.setCancelUrl("https://app.example.test/payment/cancel");
        stripe.setLiveReleaseAuthorised(false);
        stripe.setLegacyCheckoutEnabled(false);
        return stripe;
    }

    private MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        environment.setProperty("EXTERNAL_PROVIDER_MODE", "DISABLED");
        environment.setProperty("STRIPE_LIVE_RELEASE_AUTHORISED", "false");
        environment.setProperty("STRIPE_LEGACY_CHECKOUT_ENABLED", "false");
        environment.setProperty("STRIPE_API_BASE_URL", "https://api.stripe.com");
        environment.setProperty("STRIPE_API_VERSION", "2026-02-25.clover");
        environment.setProperty(
                "STRIPE_SUCCESS_URL", "https://app.example.test/payment/success");
        environment.setProperty(
                "STRIPE_CANCEL_URL", "https://app.example.test/payment/cancel");
        environment.setProperty("PAYMENT_SERVICE_URL", "http://payment-service.jsc.local:8099");
        environment.setProperty(
                "PAYMENT_SERVICE_TO_STRIPE_GATEWAY_LIFECYCLE_TOKEN",
                "payment-to-stripe-lifecycle-test-token-000001");
        return environment;
    }
}
