package com.jobseekercopilot.stripegateway.service;

import com.jobseekercopilot.stripegateway.config.ExternalProviderMode;
import com.jobseekercopilot.stripegateway.config.ExternalProviderProperties;
import com.jobseekercopilot.stripegateway.config.StripeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StripeProviderReadinessTest {

    @Test
    void liveReadinessFailsClosedUntilAllPermanentPricesAreConfigured() {
        ExternalProviderProperties provider = new ExternalProviderProperties();
        provider.setMode(ExternalProviderMode.LIVE);
        StripeProperties stripe = new StripeProperties();
        stripe.setLiveReleaseAuthorised(true);

        StripeProviderReadiness readiness = new StripeProviderReadiness(provider, stripe);

        assertThat(readiness.readiness().isCheckoutAvailable()).isFalse();
        assertThat(readiness.readiness().getCode()).isEqualTo("STRIPE_CATALOG_NOT_CONFIGURED");

        stripe.setStarterPriceId("price_starter499");
        stripe.setActivePriceId("price_active1199");
        stripe.setPowerPriceId("price_power1999");

        assertThat(readiness.readiness().isCheckoutAvailable()).isTrue();
        assertThat(readiness.readiness().getCode()).isEqualTo("READY");
    }
}
