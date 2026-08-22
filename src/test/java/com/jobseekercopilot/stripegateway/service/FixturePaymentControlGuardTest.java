package com.jobseekercopilot.stripegateway.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.stripegateway.config.ExternalProviderMode;
import com.jobseekercopilot.stripegateway.config.ExternalProviderProperties;
import com.jobseekercopilot.stripegateway.config.FixtureProperties;
import com.jobseekercopilot.stripegateway.exception.StripeConfigurationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class FixturePaymentControlGuardTest {
    private static final String TOKEN =
            "fixture-payment-control-token-000000000001";

    @Test
    void acceptsExactlyOneIndependentTokenOnlyInNonProductionFixtureMode() {
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.FIXTURE);
        FixtureProperties fixture = new FixtureProperties();
        fixture.setPaymentControlEnabled(true);
        fixture.setPaymentControlToken(TOKEN);

        MockEnvironment trusted = new MockEnvironment();
        trusted.setActiveProfiles("test");
        FixturePaymentControlGuard guard = new FixturePaymentControlGuard(
                provider, fixture, trusted);

        assertThatCode(() -> guard.requireAuthorized(List.of(TOKEN)))
                .doesNotThrowAnyException();
        assertUnavailable(() -> guard.requireAuthorized(List.of()));
        assertUnavailable(() -> guard.requireAuthorized(List.of("wrong")));
        assertUnavailable(() -> guard.requireAuthorized(List.of(TOKEN, TOKEN)));

        fixture.setPaymentControlEnabled(false);
        assertUnavailable(() -> guard.requireAuthorized(List.of(TOKEN)));
        fixture.setPaymentControlEnabled(true);

        for (String[] profiles : new String[][] {
                {}, {"default"}, {"local"}, {"demo"}, {"staging"},
                {"test", "local"}, {"test", "production"}}) {
            MockEnvironment untrusted = new MockEnvironment();
            untrusted.setActiveProfiles(profiles);
            assertUnavailable(() -> new FixturePaymentControlGuard(
                    provider, fixture, untrusted).requireAuthorized(List.of(TOKEN)));
        }
    }

    private void assertUnavailable(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(StripeConfigurationException.class)
                .hasMessageContaining("unavailable");
    }
}
