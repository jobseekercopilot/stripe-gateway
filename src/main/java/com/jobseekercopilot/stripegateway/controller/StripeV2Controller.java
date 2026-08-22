package com.jobseekercopilot.stripegateway.controller;

import com.jobseekercopilot.stripegateway.dto.CreateOwnedCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.CreateOwnedCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.dto.StripeReadinessResponse;
import com.jobseekercopilot.stripegateway.security.StripeCheckoutIdentityFilter;
import com.jobseekercopilot.stripegateway.service.StripeGatewayService;
import com.jobseekercopilot.stripegateway.service.StripeProviderReadiness;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/stripe")
@RequiredArgsConstructor
@SecurityRequirement(name = "serviceToken")
public class StripeV2Controller {
    private final StripeGatewayService stripeGatewayService;
    private final StripeProviderReadiness readiness;

    @GetMapping("/readiness")
    @Operation(operationId = "getOwnedStripeReadiness")
    public StripeReadinessResponse readiness() {
        return readiness.readiness();
    }

    @PostMapping("/checkout-sessions")
    @Operation(
            operationId = "createOwnedStripeCheckoutSession",
            parameters = @Parameter(
                    name = StripeCheckoutIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public CreateOwnedCheckoutSessionResponse checkout(
            @Parameter(
                    name = StripeCheckoutIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(StripeCheckoutIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true)
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOwnedCheckoutSessionRequest request) {
        return stripeGatewayService.createOwnedCheckoutSession(owner, idempotencyKey, request);
    }
}
