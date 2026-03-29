package com.amenbank.controller;

import com.amenbank.dto.response.*;
import com.amenbank.enums.CreditStatus;
import com.amenbank.enums.KycStatus;
import com.amenbank.enums.UserStatus;
import com.amenbank.service.impl.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin", description = "Administrative operations — requires ADMIN or SUPER_ADMIN role")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    // ─── Dashboard ────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    @Operation(summary = "Get admin dashboard stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','AUDITOR')")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Dashboard loaded", adminService.getDashboardStats()));
    }

    // ─── Users ────────────────────────────────────────────────────────
    @GetMapping("/users")
    @Operation(summary = "List all users with pagination and search")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Users loaded",
                adminService.listUsers(page, size, search, status)));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user detail")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("User loaded", adminService.getUser(id)));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Update user account status")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> updateUserStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status,
            @RequestParam(required = false) String reason) {
        adminService.updateUserStatus(id, status, reason);
        return ResponseEntity.ok(ApiResponse.ok("User status updated"));
    }

    // ─── KYC ─────────────────────────────────────────────────────────
    @GetMapping("/kyc")
    @Operation(summary = "List KYC requests")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public ResponseEntity<ApiResponse<PageResponse<KycRequestResponse>>> listKyc(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) KycStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("KYC requests loaded",
                adminService.listKycRequests(page, size, status)));
    }

    @GetMapping("/kyc/{id}")
    @Operation(summary = "Get KYC request detail")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public ResponseEntity<ApiResponse<KycRequestResponse>> getKyc(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("KYC loaded", adminService.getKycRequest(id)));
    }

    @PatchMapping("/kyc/{id}/approve")
    @Operation(summary = "Approve a KYC request")
    @PreAuthorize("hasAuthority('KYC_APPROVE')")
    public ResponseEntity<ApiResponse<Void>> approveKyc(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        adminService.approveKyc(id, userDetails != null ? userDetails.getUsername() : null);
        return ResponseEntity.ok(ApiResponse.ok("KYC approved"));
    }

    @PatchMapping("/kyc/{id}/reject")
    @Operation(summary = "Reject a KYC request")
    @PreAuthorize("hasAuthority('KYC_APPROVE')")
    public ResponseEntity<ApiResponse<Void>> rejectKyc(
            @PathVariable Long id,
            @RequestParam @NotBlank String reason) {
        adminService.rejectKyc(id, reason);
        return ResponseEntity.ok(ApiResponse.ok("KYC rejected"));
    }

    @PostMapping("/kyc/{id}/documents")
    @Operation(summary = "Upload KYC document")
    @PreAuthorize("hasAuthority('KYC_SUBMIT')")
    public ResponseEntity<ApiResponse<Void>> uploadKycDocument(
            @PathVariable Long id,
            @RequestParam String docType,
            @RequestParam("file") MultipartFile file) {
        adminService.uploadKycDocument(id, docType, file);
        return ResponseEntity.ok(ApiResponse.ok("Document uploaded"));
    }

    // ─── Credits ─────────────────────────────────────────────────────
    @GetMapping("/credits")
    @Operation(summary = "List all credit applications")
    @PreAuthorize("hasAuthority('CREDIT_REVIEW')")
    public ResponseEntity<ApiResponse<PageResponse<CreditApplicationResponse>>> listCredits(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) CreditStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Credit applications loaded",
                adminService.listCreditApplications(page, size, status)));
    }

    @PatchMapping("/credits/{id}/status")
    @Operation(summary = "Update credit application status")
    @PreAuthorize("hasAuthority('CREDIT_APPROVE')")
    public ResponseEntity<ApiResponse<CreditApplicationResponse>> updateCreditStatus(
            @PathVariable Long id,
            @RequestParam CreditStatus status,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok("Credit status updated",
                adminService.updateCreditStatus(id, status, reason)));
    }

    // ─── Audit Logs ───────────────────────────────────────────────────
    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs with filters")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId) {
        return ResponseEntity.ok(ApiResponse.ok("Audit logs loaded",
                adminService.getAuditLogs(page, size, action, actorId)));
    }
}
