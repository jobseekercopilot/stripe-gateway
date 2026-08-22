package com.jobseekercopilot.stripegateway.config;

import java.util.Arrays;
import java.util.List;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProviderModeSafety implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProviderModeSafety.class);
    private final ExternalProviderProperties providerProperties;
    private final FixtureProperties fixtureProperties;
    private final Environment environment;
    private final StripeProperties stripeProperties;

    public ProviderModeSafety(ExternalProviderProperties providerProperties, FixtureProperties fixtureProperties, Environment environment, StripeProperties stripeProperties) {
        this.providerProperties = providerProperties;
        this.fixtureProperties = fixtureProperties;
        this.environment = environment;
        this.stripeProperties = stripeProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
        if (production && providerProperties.getMode() == ExternalProviderMode.FIXTURE) {
            throw new IllegalStateException("stripe-gateway cannot start in FIXTURE mode with a production profile.");
        }
        if (fixtureProperties.isPaymentControlEnabled()) {
            String[] activeProfiles = environment.getActiveProfiles();
            boolean exactTrustedProfile = activeProfiles.length == 1
                    && "test".equalsIgnoreCase(activeProfiles[0]);
            if (!exactTrustedProfile
                    || production
                    || providerProperties.getMode() != ExternalProviderMode.FIXTURE) {
                throw new IllegalStateException(
                        "Fixture payment control requires the single active test profile and FIXTURE mode.");
            }
            if (fixtureProperties.getPaymentControlToken() == null
                    || fixtureProperties.getPaymentControlToken().length() < 32
                    || fixtureProperties.getWebhookSecret() == null
                    || fixtureProperties.getWebhookSecret().length() < 32) {
                throw new IllegalStateException(
                        "Fixture payment control requires dedicated token and webhook secret values of at least 32 characters.");
            }
        }
        if (production) {
            requireExplicitProductionSettings(
                    "EXTERNAL_PROVIDER_MODE",
                    "STRIPE_LIVE_RELEASE_AUTHORISED",
                    "STRIPE_LEGACY_CHECKOUT_ENABLED",
                    "STRIPE_API_BASE_URL",
                    "STRIPE_API_VERSION",
                    "STRIPE_SUCCESS_URL",
                    "STRIPE_CANCEL_URL",
                    "PAYMENT_SERVICE_URL",
                    "PAYMENT_SERVICE_TO_STRIPE_GATEWAY_LIFECYCLE_TOKEN");
            requireStripeApiUrl(stripeProperties.getApiBaseUrl());
            requireHttpsReturnUrl(stripeProperties.getSuccessUrl(), "success");
            requireHttpsReturnUrl(stripeProperties.getCancelUrl(), "cancel");
            requireInternalServiceUrl(environment.getProperty("PAYMENT_SERVICE_URL"));
        }
        if (providerProperties.getMode() == ExternalProviderMode.LIVE) {
            if (!stripeProperties.isLiveReleaseAuthorised()) {
                throw new IllegalStateException(
                        "Stripe LIVE mode requires explicit release authorisation.");
            }
            String requiredKeyPrefix = production ? "sk_live_" : "sk_test_";
            if (stripeProperties.getSecretKey() == null
                    || !stripeProperties.getSecretKey().startsWith(requiredKeyPrefix)) {
                throw new IllegalStateException(production
                        ? "Stripe production LIVE mode requires a live secret key."
                        : "Stripe non-production network mode requires a test secret key.");
            }
            if (stripeProperties.getWebhookSecret() == null
                    || !stripeProperties.getWebhookSecret().startsWith("whsec_")) {
                throw new IllegalStateException("Stripe LIVE mode requires a webhook signing secret.");
            }
            if (stripeProperties.getApiVersion() == null
                    || stripeProperties.getApiVersion().isBlank()) {
                throw new IllegalStateException("Stripe LIVE mode requires a pinned API version.");
            }
            requireHttpsReturnUrl(stripeProperties.getSuccessUrl(), "success");
            requireHttpsReturnUrl(stripeProperties.getCancelUrl(), "cancel");
        }
        if (production && stripeProperties.isLegacyCheckoutEnabled()) {
            throw new IllegalStateException("Legacy caller-priced Stripe Checkout is forbidden in production.");
        }
        log.info("provider mode active gateway=stripe-gateway mode={} datasetId={} datasetVersion={} scenario={} externalCallsEnabled={}",
                providerProperties.getMode(), fixtureProperties.getDatasetId(), fixtureProperties.getDatasetVersion(),
                fixtureProperties.getScenario(), providerProperties.getMode() == ExternalProviderMode.LIVE);
    }

    private void requireHttpsReturnUrl(String value, String label) {
        requireHttpsUrl(value, label);
        URI uri = URI.create(value);
        if (uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException(
                    "Stripe " + label
                            + " URL must not include credentials, a query, or a fragment.");
        }
        if (localHost(uri.getHost())) {
            throw new IllegalStateException(
                    "Stripe " + label + " URL must not use a local host in production.");
        }
    }

    private void requireStripeApiUrl(String value) {
        requireHttpsUrl(value, "API base");
        URI uri = URI.create(value);
        if (!"api.stripe.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank()
                && !"/".equals(uri.getPath()))) {
            throw new IllegalStateException(
                    "Stripe API base URL must be the credential-free api.stripe.com HTTPS origin.");
        }
    }

    private void requireInternalServiceUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || localHost(uri.getHost())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException invalid) {
            throw new IllegalStateException(
                    "Payment Service URL must be an absolute non-local HTTP(S) origin.");
        }
    }

    private boolean localHost(String host) {
        String normalised = host == null ? "" : host.toLowerCase(java.util.Locale.ROOT);
        return host == null
                || "localhost".equals(normalised)
                || normalised.endsWith(".localhost")
                || "127.0.0.1".equals(normalised)
                || "0.0.0.0".equals(normalised)
                || "::1".equals(normalised);
    }

    private void requireHttpsUrl(String value, String label) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Stripe " + label + " URL must be an absolute HTTPS URL.");
        }
    }

    private void requireExplicitProductionSettings(String... names) {
        List<String> missing = Arrays.stream(names)
                .filter(name -> {
                    String value = environment.getProperty(name);
                    return value == null || value.isBlank();
                })
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Production Stripe settings must be explicit: " + String.join(", ", missing));
        }
    }
}
