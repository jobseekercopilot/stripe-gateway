package com.jobseekercopilot.stripegateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class StripeCheckoutIdentityFilter extends OncePerRequestFilter {
    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    public static final String OWNER_HEADER = "X-Payment-Owner";
    public static final String OWNER_ATTRIBUTE = "stripePaymentOwner";

    private static final String CHECKOUT_PATH = "/api/v1/stripe/checkout-sessions";
    private static final String OWNED_CHECKOUT_PATH = "/api/v2/stripe/checkout-sessions";
    private static final String READINESS_PATH = "/api/v2/stripe/readiness";
    private static final String LIFECYCLE_EXPIRE_PATH =
            "/internal/v2/stripe/checkout-sessions/expire";
    private static final String LEGACY_OWNER_HEADER = "X-User-Id";
    private static final int MAXIMUM_OWNER_LENGTH = 128;

    private final StripeGatewayCredentials credentials;
    private final ObjectMapper objectMapper;

    public StripeCheckoutIdentityFilter(
            StripeGatewayCredentials credentials,
            ObjectMapper objectMapper) {
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals(CHECKOUT_PATH)
                && !path.equals(OWNED_CHECKOUT_PATH)
                && !path.equals(READINESS_PATH)
                && !path.equals(LIFECYCLE_EXPIRE_PATH);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        List<String> tokens = headers(request, SERVICE_TOKEN_HEADER);
        boolean lifecycle = request.getRequestURI().equals(LIFECYCLE_EXPIRE_PATH);
        if (tokens.size() != 1
                || !(lifecycle
                ? credentials.authenticatesPaymentLifecycle(tokens.get(0))
                : credentials.authenticatesPaymentGateway(tokens.get(0)))) {
            reject(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "SERVICE_AUTHENTICATION_REQUIRED",
                    lifecycle
                            ? "Valid Payment Service lifecycle authentication is required."
                            : "Valid Payment Gateway authentication is required.");
            return;
        }

        if (!headers(request, LEGACY_OWNER_HEADER).isEmpty()) {
            reject(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "CALLER_IDENTITY_REJECTED",
                    "Caller-selected identity is not accepted.");
            return;
        }

        if (!request.getRequestURI().equals(READINESS_PATH)) {
            List<String> owners = headers(request, OWNER_HEADER);
            if (owners.size() != 1 || !validOwner(owners.get(0))) {
                reject(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "PAYMENT_OWNER_REQUIRED",
                        "Exactly one valid payment owner is required.");
                return;
            }
            request.setAttribute(OWNER_ATTRIBUTE, owners.get(0).trim());
        }
        filterChain.doFilter(request, response);
    }

    private boolean validOwner(String owner) {
        if (!StringUtils.hasText(owner)) {
            return false;
        }
        String trimmed = owner.trim();
        return trimmed.length() <= MAXIMUM_OWNER_LENGTH
                && !trimmed.contains(",")
                && trimmed.chars().noneMatch(Character::isISOControl);
    }

    private List<String> headers(HttpServletRequest request, String name) {
        return Collections.list(request.getHeaders(name));
    }

    private void reject(
            HttpServletResponse response,
            int status,
            String code,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new StripeIdentityError(code, message));
    }
}
