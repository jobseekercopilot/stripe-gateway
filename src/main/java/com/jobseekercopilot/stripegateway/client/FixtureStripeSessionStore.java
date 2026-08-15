package com.jobseekercopilot.stripegateway.client;

import com.jobseekercopilot.stripegateway.dto.PaymentOrderSnapshot;
import com.jobseekercopilot.stripegateway.dto.StripeCheckoutSession;
import com.jobseekercopilot.stripegateway.exception.BadRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** In-memory provider state used only by the explicitly selected FIXTURE client. */
@Component
@ConditionalOnProperty(prefix = "external-provider", name = "mode", havingValue = "FIXTURE")
public class FixtureStripeSessionStore {
    private final Map<String, FixtureSession> sessions = new LinkedHashMap<>();

    public synchronized StripeCheckoutSession create(PaymentOrderSnapshot order) {
        String suffix = order.getOrderId().toString().replace("-", "");
        String sessionId = "cs_fixture_" + suffix;
        FixtureSession existing = sessions.get(sessionId);
        if (existing != null) {
            if (!existing.order().getOrderId().equals(order.getOrderId())
                    || !existing.order().getOwnerId().equals(order.getOwnerId())) {
                throw new BadRequestException("Fixture Checkout idempotency conflict");
            }
            return response(existing);
        }
        FixtureSession created = new FixtureSession(
                order,
                sessionId,
                "pi_fixture_" + suffix,
                "open",
                "unpaid",
                null,
                null);
        sessions.put(sessionId, created);
        return response(created);
    }

    public synchronized StripeCheckoutSession retrieve(String sessionId) {
        return response(require(sessionId));
    }

    public synchronized StripeCheckoutSession expire(String sessionId) {
        FixtureSession current = require(sessionId);
        if ("complete".equals(current.status())) {
            return response(current);
        }
        FixtureSession expired = current.withTerminal("expired", "unpaid", Instant.now());
        sessions.put(sessionId, expired);
        return response(expired);
    }

    public synchronized FixtureTerminalSnapshot terminal(
            String sessionId, FixtureTerminalEvent event) {
        FixtureSession current = require(sessionId);
        String requiredStatus = event == FixtureTerminalEvent.COMPLETED
                ? "complete" : "expired";
        String oppositeStatus = event == FixtureTerminalEvent.COMPLETED
                ? "expired" : "complete";
        if (oppositeStatus.equals(current.status())) {
            throw new BadRequestException(
                    "Fixture Checkout session is already terminal as " + oppositeStatus);
        }
        FixtureSession terminal = current;
        if (!requiredStatus.equals(current.status())) {
            terminal = current.withTerminal(
                    requiredStatus,
                    event == FixtureTerminalEvent.COMPLETED ? "paid" : "unpaid",
                    Instant.now());
            sessions.put(sessionId, terminal);
        }
        String eventId = "evt_fixture_" + event.name().toLowerCase()
                + "_" + terminal.order().getOrderId().toString().replace("-", "");
        return new FixtureTerminalSnapshot(
                terminal.order(), response(terminal), event, eventId,
                terminal.terminalAt());
    }

    public synchronized int resetOwner(String owner) {
        List<String> owned = sessions.entrySet().stream()
                .filter(entry -> entry.getValue().order().getOwnerId().equals(owner))
                .map(Map.Entry::getKey)
                .toList();
        owned.forEach(sessions::remove);
        return owned.size();
    }

    public synchronized FixtureOwnerStatus ownerStatus(String owner) {
        List<StripeCheckoutSession> owned = new ArrayList<>();
        sessions.values().stream()
                .filter(session -> session.order().getOwnerId().equals(owner))
                .map(this::response)
                .forEach(owned::add);
        return new FixtureOwnerStatus(owner, List.copyOf(owned));
    }

    private FixtureSession require(String sessionId) {
        FixtureSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BadRequestException("Fixture Checkout session was not found");
        }
        return session;
    }

    private StripeCheckoutSession response(FixtureSession fixture) {
        StripeCheckoutSession session = new StripeCheckoutSession();
        session.setId(fixture.sessionId());
        session.setUrl("https://checkout.stripe.test/fixture/" + fixture.sessionId());
        session.setPaymentIntent(fixture.paymentIntentId());
        session.setStatus(fixture.status());
        session.setPaymentStatus(fixture.paymentStatus());
        session.setExpiresAt(fixture.order().getExpiresAt().getEpochSecond());
        return session;
    }

    private record FixtureSession(
            PaymentOrderSnapshot order,
            String sessionId,
            String paymentIntentId,
            String status,
            String paymentStatus,
            Instant terminalAt,
            UUID transitionId) {
        private FixtureSession withTerminal(
                String nextStatus, String nextPaymentStatus, Instant at) {
            return new FixtureSession(
                    order, sessionId, paymentIntentId, nextStatus,
                    nextPaymentStatus, terminalAt == null ? at : terminalAt,
                    transitionId == null ? UUID.randomUUID() : transitionId);
        }
    }

    public enum FixtureTerminalEvent { COMPLETED, EXPIRED }

    public record FixtureTerminalSnapshot(
            PaymentOrderSnapshot order,
            StripeCheckoutSession session,
            FixtureTerminalEvent event,
            String eventId,
            Instant eventCreatedAt) {}

    public record FixtureOwnerStatus(
            String owner,
            List<StripeCheckoutSession> sessions) {}
}
