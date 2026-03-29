package com.amenbank.controller;

import com.amenbank.dto.request.CreditApplicationRequest;
import com.amenbank.dto.request.CreditSimulationRequest;
import com.amenbank.dto.response.*;
import com.amenbank.service.impl.CreditService;
import com.amenbank.service.impl.UserDetailsServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/credits")
@RequiredArgsConstructor
@Validated
@Tag(name = "Credits", description = "Credit simulation and applications")
@SecurityRequirement(name = "bearerAuth")
public class CreditController {

    private final CreditService creditService;
    private final UserDetailsServiceImpl userDetailsService;

    @PostMapping("/simulate")
    @Operation(summary = "Simulate credit offer with amortization table")
    @PreAuthorize("hasAuthority('CREDIT_SIMULATE')")
    public ResponseEntity<ApiResponse<CreditSimulationResponse>> simulate(
            @Valid @RequestBody CreditSimulationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Simulation computed", creditService.simulate(request)));
    }

    @PostMapping("/apply")
    @Operation(summary = "Submit a credit application")
    @PreAuthorize("hasAuthority('CREDIT_APPLY')")
    public ResponseEntity<ApiResponse<CreditApplicationResponse>> apply(
            @Valid @RequestBody CreditApplicationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Application submitted", creditService.apply(request, user.getId())));
    }

    @GetMapping
    @Operation(summary = "Get all credit applications for current user")
    @PreAuthorize("hasAuthority('CREDIT_APPLY')")
    public ResponseEntity<ApiResponse<PageResponse<CreditApplicationResponse>>> getApplications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Applications loaded",
                creditService.getUserApplications(user.getId(), page, size)));
    }
}
