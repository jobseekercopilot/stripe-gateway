package com.jobseekercopilot.stripegateway.controller;

import com.jobseekercopilot.stripegateway.dto.ExpireOwnedCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.ExpireOwnedCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.security.StripeCheckoutIdentityFilter;
import com.jobseekercopilot.stripegateway.service.StripeGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v2/stripe/checkout-sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "serviceToken")
public class StripeLifecycleController {
    private final StripeGatewayService stripeGatewayService;

    @PostMapping("/expire")
    @Operation(
            operationId = "expireOwnedStripeCheckoutSession",
            parameters = @Parameter(
                    name = StripeCheckoutIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public ExpireOwnedCheckoutSessionResponse expire(
            @Parameter(
                    name = StripeCheckoutIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(StripeCheckoutIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @Valid @RequestBody ExpireOwnedCheckoutSessionRequest request) {
        return stripeGatewayService.expireOwnedCheckoutSession(owner, request);
    }
}
