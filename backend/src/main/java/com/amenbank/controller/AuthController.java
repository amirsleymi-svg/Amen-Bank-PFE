package com.amenbank.controller;

import com.amenbank.dto.request.*;
import com.amenbank.dto.response.ApiResponse;
import com.amenbank.dto.response.AuthResponse;
import com.amenbank.dto.response.TotpSetupResponse;
import com.amenbank.dto.response.UserResponse;
import java.util.List;
import java.util.Optional;
import com.amenbank.entity.Admin;
import com.amenbank.entity.User;
import com.amenbank.exception.UnauthorizedException;
import com.amenbank.repository.AdminRepository;
import com.amenbank.service.impl.AdminService;
import com.amenbank.service.impl.AuthService;
import com.amenbank.service.impl.UserDetailsServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, 2FA, token management")
public class AuthController {

    private final AuthService authService;
    private final UserDetailsServiceImpl userDetailsService;
    private final AdminRepository adminRepository;
    private final AdminService adminService;

    // ─── Register (Admin only) ────────────────────────────────────────
    @PostMapping("/register")
    @Operation(summary = "Register a new user account (admin only)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful. Please verify your email.", user));
    }

    // ─── Login ────────────────────────────────────────────────────────
    @PostMapping("/login")
    @Operation(summary = "Authenticate with email/username + password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(
                request,
                getClientIp(httpRequest),
                request.getDeviceInfo()
        );
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    // ─── Verify TOTP ──────────────────────────────────────────────────
    @PostMapping("/totp/verify")
    @Operation(summary = "Complete 2FA login step with TOTP code")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyTotp(
            @Valid @RequestBody TotpVerifyRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.verifyTotp(
                request,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(ApiResponse.ok("2FA verification successful", response));
    }

    // ─── Refresh Token ────────────────────────────────────────────────
    @PostMapping("/refresh")
    @Operation(summary = "Obtain new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.refreshToken(
                request,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", response));
    }

    // ─── Logout ───────────────────────────────────────────────────────
    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate refresh token", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) throw new UnauthorizedException("Authentication required");
        Optional<Admin> adminOpt = adminRepository.findByEmailOrUsername(userDetails.getUsername());
        if (adminOpt.isPresent()) {
            authService.logoutAdmin(request.getRefreshToken(), adminOpt.get().getId());
        } else {
            User user = requireAuthenticatedUser(userDetails);
            authService.logout(request.getRefreshToken(), user.getId());
        }
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }

    // ─── Setup TOTP ───────────────────────────────────────────────────
    @PostMapping("/totp/setup")
    @Operation(summary = "Generate TOTP secret and QR code for 2FA enrollment",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<TotpSetupResponse>> setupTotp(
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        User user = requireClientUser(userDetails);
        TotpSetupResponse response = authService.setupTotp(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("TOTP setup initiated. Scan QR code with Google Authenticator.", response));
    }

    // ─── Enable TOTP ──────────────────────────────────────────────────
    @PostMapping("/totp/enable")
    @Operation(summary = "Confirm and enable 2FA by verifying initial TOTP code — returns one-time backup codes",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<String>>> enableTotp(
            @RequestParam String totpCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = requireClientUser(userDetails);
        List<String> backupCodes = authService.enableTotp(user.getId(), totpCode);
        return ResponseEntity.ok(ApiResponse.ok(
                "Two-factor authentication has been enabled on your account.", backupCodes));
    }

    // ─── Disable TOTP ─────────────────────────────────────────────────
    @PostMapping("/totp/disable")
    @Operation(summary = "Disable 2FA — requires current TOTP code to confirm",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> disableTotp(
            @RequestParam String totpCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = requireClientUser(userDetails);
        authService.disableTotp(user.getId(), totpCode);
        return ResponseEntity.ok(ApiResponse.ok("Two-factor authentication has been disabled."));
    }

    // ─── Change Password ──────────────────────────────────────────────
    @PostMapping("/password/change")
    @Operation(summary = "Change password — requires current password and TOTP if 2FA is enabled",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = requireClientUser(userDetails);
        authService.changePassword(user.getId(),
                request.getCurrentPassword(), request.getNewPassword(), request.getTotpCode());
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully. Please log in again on other devices."));
    }

    // ─── Revoke All Sessions ──────────────────────────────────────────
    @PostMapping("/sessions/revoke-all")
    @Operation(summary = "Revoke all refresh tokens — signs out all other devices",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = requireClientUser(userDetails);
        authService.revokeAllSessions(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("All other sessions have been revoked."));
    }

    // ─── Forgot Password ──────────────────────────────────────────────
    @PostMapping("/password/forgot")
    @Operation(summary = "Request password reset email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "If your email is registered, you will receive a password reset link shortly."));
    }

    // ─── Reset Password ───────────────────────────────────────────────
    @PostMapping("/password/reset")
    @Operation(summary = "Reset password using token from email")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password has been reset successfully. Please login with your new password."));
    }

    // ─── Verify Email ─────────────────────────────────────────────────
    @GetMapping("/email/verify")
    @Operation(summary = "Verify email address using token from email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok("Email verified successfully."));
    }

    // ─── Me ───────────────────────────────────────────────────────────
    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile (works for clients and admins)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<?>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) throw new UnauthorizedException("Authentication required");

        Optional<Admin> adminOpt = adminRepository.findByEmailOrUsername(userDetails.getUsername());
        if (adminOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.ok("Profile loaded",
                    adminService.getAdmin(adminOpt.get().getId())));
        }

        User user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus())
                .totpEnabled(user.getTotpEnabled())
                .emailVerified(user.getEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Profile loaded", response));
    }

    // ─── Mandatory TOTP enrollment (login → no 2FA configured) ───────
    @PostMapping("/totp/mandatory-setup")
    @Operation(summary = "Initialize TOTP for first-time mandatory 2FA enrollment")
    public ResponseEntity<ApiResponse<TotpSetupResponse>> mandatorySetupTotp(
            @Valid @RequestBody TotpMandatorySetupRequest request) throws Exception {
        TotpSetupResponse response = authService.mandatorySetupTotp(request.getSetupToken());
        return ResponseEntity.ok(ApiResponse.ok(
                "Scan the QR code with your authenticator app, then call /auth/totp/mandatory-enable.",
                response));
    }

    @PostMapping("/totp/mandatory-enable")
    @Operation(summary = "Confirm mandatory 2FA enrollment and receive full access tokens + backup codes")
    public ResponseEntity<ApiResponse<AuthResponse>> mandatoryEnableTotp(
            @Valid @RequestBody TotpMandatoryEnableRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.mandatoryEnableTotp(
                request.getSetupToken(),
                request.getTotpCode(),
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(ApiResponse.ok(
                "2FA enabled. Store your backup codes safely — they will not be shown again.", response));
    }

    // ─── Helpers ──────────────────────────────────────────────────────
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) return request.getRemoteAddr();
        return xfHeader.split(",")[0].trim();
    }

    /** Returns the authenticated User entity. Throws if the principal is an admin/employee. */
    private User requireClientUser(UserDetails userDetails) {
        if (userDetails == null) throw new UnauthorizedException("Authentication required");
        if (adminRepository.existsByEmail(userDetails.getUsername())) {
            throw new UnauthorizedException("This operation is only available for client accounts");
        }
        return userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
    }

    private User requireAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) throw new UnauthorizedException("Authentication required");
        return userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
    }
}
