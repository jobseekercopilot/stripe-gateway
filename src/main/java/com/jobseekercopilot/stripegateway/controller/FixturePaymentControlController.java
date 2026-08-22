package com.jobseekercopilot.stripegateway.controller;

import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore;
import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore.FixtureOwnerStatus;
import com.jobseekercopilot.stripegateway.client.FixtureStripeSessionStore.FixtureTerminalEvent;
import com.jobseekercopilot.stripegateway.service.FixturePaymentControlGuard;
import com.jobseekercopilot.stripegateway.service.FixturePaymentControlService;
import com.jobseekercopilot.stripegateway.service.FixturePaymentControlService.FixturePaymentEventResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/fixtures/v2/stripe")
@ConditionalOnProperty(prefix = "external-provider", name = "mode", havingValue = "FIXTURE")
public class FixturePaymentControlController {
    private final FixturePaymentControlGuard guard;
    private final FixturePaymentControlService controlService;
    private final FixtureStripeSessionStore sessionStore;

    public FixturePaymentControlController(
            FixturePaymentControlGuard guard,
            FixturePaymentControlService controlService,
            FixtureStripeSessionStore sessionStore) {
        this.guard = guard;
        this.controlService = controlService;
        this.sessionStore = sessionStore;
    }

    @PostMapping("/checkout-sessions/{sessionId}/events")
    public ResponseEntity<FixturePaymentEventResponse> event(
            HttpServletRequest servletRequest,
            @PathVariable String sessionId,
            @RequestBody FixturePaymentEventRequest request) {
        authorize(servletRequest);
        if (request == null || request.event() == null) {
            throw new com.jobseekercopilot.stripegateway.exception.BadRequestException(
                    "Fixture payment event is required");
        }
        return ResponseEntity.ok(controlService.emit(sessionId, request.event()));
    }

    @DeleteMapping("/owners/{owner}")
    public ResponseEntity<Map<String, Object>> reset(
            HttpServletRequest servletRequest, @PathVariable String owner) {
        authorize(servletRequest);
        int removed = sessionStore.resetOwner(owner);
        return ResponseEntity.ok(Map.of(
                "service", "stripe-gateway",
                "operation", "RESET",
                "status", "SUCCESS",
                "recordsAffected", removed,
                "details", Map.of("owner", owner, "sessionsRemoved", removed),
                "warnings", java.util.List.of()));
    }

    @GetMapping("/owners/{owner}")
    public ResponseEntity<Map<String, Object>> status(
            HttpServletRequest servletRequest, @PathVariable String owner) {
        authorize(servletRequest);
        FixtureOwnerStatus status = sessionStore.ownerStatus(owner);
        return ResponseEntity.ok(Map.of(
                "service", "stripe-gateway",
                "operation", "VERIFY",
                "status", "SUCCESS",
                "recordsAffected", status.sessions().size(),
                "details", Map.of("owner", owner, "sessions", status.sessions()),
                "warnings", java.util.List.of()));
    }

    private void authorize(HttpServletRequest request) {
        guard.requireAuthorized(Collections.list(
                request.getHeaders(FixturePaymentControlGuard.TOKEN_HEADER)));
    }

    public record FixturePaymentEventRequest(FixtureTerminalEvent event) {}
}
