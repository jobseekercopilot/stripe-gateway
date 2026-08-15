package com.jobseekercopilot.stripegateway.service;

import com.jobseekercopilot.stripegateway.config.ExternalProviderMode;
import com.jobseekercopilot.stripegateway.config.ExternalProviderProperties;
import com.jobseekercopilot.stripegateway.config.FixtureProperties;
import com.jobseekercopilot.stripegateway.exception.StripeConfigurationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class FixturePaymentControlGuard {
    public static final String TOKEN_HEADER = "X-Environment-Data-Token";

    private final ExternalProviderProperties providerProperties;
    private final FixtureProperties fixtureProperties;
    private final Environment environment;

    public FixturePaymentControlGuard(
            ExternalProviderProperties providerProperties,
            FixtureProperties fixtureProperties,
            Environment environment) {
        this.providerProperties = providerProperties;
        this.fixtureProperties = fixtureProperties;
        this.environment = environment;
    }

    public void requireAuthorized(List<String> suppliedTokens) {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean exactTrustedProfile = activeProfiles.length == 1
                && "test".equalsIgnoreCase(activeProfiles[0]);
        String expected = fixtureProperties.getPaymentControlToken();
        if (!exactTrustedProfile
                || providerProperties.getMode() != ExternalProviderMode.FIXTURE
                || !fixtureProperties.isPaymentControlEnabled()
                || expected == null
                || expected.length() < 32
                || suppliedTokens == null
                || suppliedTokens.size() != 1
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        suppliedTokens.get(0).getBytes(StandardCharsets.UTF_8))) {
            throw new StripeConfigurationException(
                    "Fixture payment control is unavailable");
        }
    }
}
