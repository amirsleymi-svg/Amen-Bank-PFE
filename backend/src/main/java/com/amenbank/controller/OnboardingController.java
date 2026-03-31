package com.amenbank.controller;

import com.amenbank.dto.request.ActivateAccountRequest;
import com.amenbank.dto.request.CreateAccountFromRequestDto;
import com.amenbank.dto.request.RegistrationRequestDto;
import com.amenbank.dto.response.ApiResponse;
import com.amenbank.dto.response.PageResponse;
import com.amenbank.dto.response.RegistrationRequestResponse;
import com.amenbank.dto.response.UserResponse;
import com.amenbank.enums.RegistrationStatus;
import com.amenbank.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/onboarding")
@Validated
@Tag(name = "Onboarding", description = "Client registration request and account activation flow")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    // ─── PUBLIC: Client submits registration request ───────────────────
    @PostMapping("/register")
    @Operation(summary = "Submit a registration request (email + confirmEmail)")
    public ResponseEntity<ApiResponse<RegistrationRequestResponse>> submitRequest(
            @Valid @RequestBody RegistrationRequestDto dto,
            HttpServletRequest request) {

        String ip = getClientIp(request);
        RegistrationRequestResponse response = onboardingService.submitRegistrationRequest(dto, ip);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        "Your registration request has been submitted. An administrator will review it shortly.",
                        response));
    }

    // ─── PUBLIC: Client activates account via email token ─────────────
    @PostMapping("/activate")
    @Operation(summary = "Activate account using email token and set password")
    public ResponseEntity<ApiResponse<Void>> activateAccount(
            @Valid @RequestBody ActivateAccountRequest request) {
        onboardingService.activateAccount(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Your account has been activated successfully! You can now log in."));
    }

    // ─── ADMIN: List registration requests ────────────────────────────
    @GetMapping("/requests")
    @Operation(summary = "List all registration requests (admin)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('USER_READ', 'USER_MANAGE')")
    public ResponseEntity<ApiResponse<PageResponse<RegistrationRequestResponse>>> listRequests(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) RegistrationStatus status) {

        return ResponseEntity.ok(ApiResponse.ok("Registration requests loaded",
                onboardingService.listRegistrationRequests(page, size, status)));
    }

    // ─── ADMIN: Create account from request ───────────────────────────
    @PostMapping("/requests/create-account")
    @Operation(summary = "Create user account from a registration request (admin only)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAuthority('ACCOUNT_CREATE')")
    public ResponseEntity<ApiResponse<UserResponse>> createAccount(
            @Valid @RequestBody CreateAccountFromRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        UserResponse response = onboardingService.createAccountFromRequest(dto, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created. Activation email has been sent to the client.", response));
    }

    // ─── ADMIN: Reject registration request ───────────────────────────
    @PatchMapping("/requests/{id}/reject")
    @Operation(summary = "Reject a registration request (admin only)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAuthority('ACCOUNT_CREATE')")
    public ResponseEntity<ApiResponse<Void>> rejectRequest(
            @PathVariable Long id,
            @RequestParam @NotBlank String reason,
            @AuthenticationPrincipal UserDetails userDetails) {

        onboardingService.rejectRegistrationRequest(id, reason, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Registration request rejected."));
    }

    // ─── Helper ───────────────────────────────────────────────────────
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
