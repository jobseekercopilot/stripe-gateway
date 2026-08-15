package com.jobseekercopilot.stripegateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore.FixtureTerminalEvent;
import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.stripegateway.exception.BadRequestException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FixtureStripeSessionStoreTest {
    private final FixtureStripeSessionStore store = new FixtureStripeSessionStore();

    @Test
    void createsOneOrderBoundSessionAndReplaysItsTerminalState() {
        PaymentOrderSnapshot order = order("owner-a");

        var created = store.create(order);
        var replay = store.create(order);
        var completed = store.terminal(created.getId(), FixtureTerminalEvent.COMPLETED);
        var completedReplay = store.terminal(created.getId(), FixtureTerminalEvent.COMPLETED);

        assertThat(replay.getId()).isEqualTo(created.getId());
        assertThat(created.getId()).isEqualTo(
                "cs_fixture_" + order.getOrderId().toString().replace("-", ""));
        assertThat(completed.session().getStatus()).isEqualTo("complete");
        assertThat(completed.session().getPaymentStatus()).isEqualTo("paid");
        assertThat(completedReplay.eventId()).isEqualTo(completed.eventId());
        assertThat(completedReplay.eventCreatedAt()).isEqualTo(completed.eventCreatedAt());
        assertThatThrownBy(() -> store.terminal(
                        created.getId(), FixtureTerminalEvent.EXPIRED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already terminal");
    }

    @Test
    void expiresAndResetsOnlyTheExactOwner() {
        var first = store.create(order("owner-a"));
        var second = store.create(order("owner-b"));

        assertThat(store.expire(first.getId()).getStatus()).isEqualTo("expired");
        assertThat(store.resetOwner("owner-a")).isEqualTo(1);
        assertThat(store.ownerStatus("owner-a").sessions()).isEmpty();
        assertThat(store.ownerStatus("owner-b").sessions())
                .extracting(session -> session.getId())
                .containsExactly(second.getId());
    }

    private PaymentOrderSnapshot order(String owner) {
        PaymentOrderSnapshot order = new PaymentOrderSnapshot();
        order.setOrderId(UUID.randomUUID());
        order.setOwnerId(owner);
        order.setPricingPlanId("starter");
        order.setDocumentCredits(10);
        order.setPromotionBonusDocumentCredits(5);
        order.setPriceMinor(799);
        order.setCurrency("GBP");
        order.setBillingCountry("GB");
        order.setExpiresAt(Instant.now().plusSeconds(3600));
        return order;
    }
}
