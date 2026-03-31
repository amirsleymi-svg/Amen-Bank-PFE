package com.amenbank.controller;

import com.amenbank.dto.request.CreateAdminRequest;
import com.amenbank.dto.response.*;
import com.amenbank.enums.AdminRole;
import com.amenbank.enums.CreditStatus;
import com.amenbank.enums.KycStatus;
import com.amenbank.enums.UserStatus;
import com.amenbank.service.impl.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin", description = "Administrative operations — requires ADMIN or EMPLOYEE role")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    // ─── Dashboard ────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    @Operation(summary = "Get admin dashboard statistics")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Dashboard loaded", adminService.getDashboardStats()));
    }

    // ─── Users ────────────────────────────────────────────────────────
    @GetMapping("/users")
    @Operation(summary = "List users with optional search and status filter")
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
    @Operation(summary = "Get user details by ID")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("User loaded", adminService.getUser(id)));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Update a user's account status (ACTIVE, SUSPENDED, DELETED)")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> updateUserStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails userDetails) {
        adminService.updateUserStatus(id, status, reason, actorEmail(userDetails));
        return ResponseEntity.ok(ApiResponse.ok("User status updated"));
    }

    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "Reset a user's password and send temporary password by email")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> resetUserPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        adminService.resetUserPassword(id, actorEmail(userDetails));
        return ResponseEntity.ok(ApiResponse.ok("Password reset. Temporary password sent by email."));
    }

    // ─── KYC ─────────────────────────────────────────────────────────
    @GetMapping("/kyc")
    @Operation(summary = "List KYC requests with optional status filter")
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
    @Operation(summary = "Approve a KYC request — activates the user account")
    @PreAuthorize("hasAuthority('KYC_APPROVE')")
    public ResponseEntity<ApiResponse<Void>> approveKyc(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        adminService.approveKyc(id, actorEmail(userDetails));
        return ResponseEntity.ok(ApiResponse.ok("KYC approved"));
    }

    @PatchMapping("/kyc/{id}/reject")
    @Operation(summary = "Reject a KYC request — reason is mandatory")
    @PreAuthorize("hasAuthority('KYC_APPROVE')")
    public ResponseEntity<ApiResponse<Void>> rejectKyc(
            @PathVariable Long id,
            @RequestParam @NotBlank String reason,
            @AuthenticationPrincipal UserDetails userDetails) {
        adminService.rejectKyc(id, reason, actorEmail(userDetails));
        return ResponseEntity.ok(ApiResponse.ok("KYC rejected"));
    }

    @PostMapping("/kyc/{id}/documents")
    @Operation(summary = "Upload a supporting document for a KYC request")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public ResponseEntity<ApiResponse<Void>> uploadKycDocument(
            @PathVariable Long id,
            @RequestParam String docType,
            @RequestParam MultipartFile file) {
        adminService.uploadKycDocument(id, docType, file);
        return ResponseEntity.ok(ApiResponse.ok("Document uploaded"));
    }

    // ─── Credits ─────────────────────────────────────────────────────
    @GetMapping("/credits")
    @Operation(summary = "List credit applications with optional status filter")
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

    // ─── Admin management ─────────────────────────────────────────────
    @GetMapping("/admins")
    @Operation(summary = "List all administrators (role = ADMIN)")
    @PreAuthorize("hasAuthority('ADMIN_READ')")
    public ResponseEntity<ApiResponse<PageResponse<AdminResponse>>> listAdmins(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok("Admins loaded", adminService.listAdmins(page, size)));
    }

    @GetMapping("/admins/{id}")
    @Operation(summary = "Get administrator or employee by ID")
    @PreAuthorize("hasAuthority('ADMIN_READ')")
    public ResponseEntity<ApiResponse<AdminResponse>> getAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Admin loaded", adminService.getAdmin(id)));
    }

    @PostMapping("/admins")
    @Operation(summary = "Create a new administrator account")
    @PreAuthorize("hasAuthority('ADMIN_CREATE')")
    public ResponseEntity<ApiResponse<AdminResponse>> createAdmin(
            @Valid @RequestBody CreateAdminRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        AdminResponse created = adminService.createAdmin(req, actorEmail(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Admin created", created));
    }

    @PatchMapping("/admins/{id}/role")
    @Operation(summary = "Change an administrator's or employee's role")
    @PreAuthorize("hasAuthority('ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<AdminResponse>> updateAdminRole(
            @PathVariable Long id,
            @RequestParam AdminRole role,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok("Role updated",
                adminService.updateAdminRole(id, role, actorEmail(userDetails))));
    }

    @PatchMapping("/admins/{id}/status")
    @Operation(summary = "Enable or disable an administrator or employee account")
    @PreAuthorize("hasAuthority('ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<AdminResponse>> toggleAdminStatus(
            @PathVariable Long id,
            @RequestParam boolean active,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                adminService.toggleAdminStatus(id, active, actorEmail(userDetails))));
    }

    // ─── Employee management ──────────────────────────────────────────
    @GetMapping("/employees")
    @Operation(summary = "List all bank employees (role = EMPLOYEE)")
    @PreAuthorize("hasAuthority('ADMIN_READ')")
    public ResponseEntity<ApiResponse<PageResponse<AdminResponse>>> listEmployees(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok("Employees loaded",
                adminService.listEmployees(page, size)));
    }

    @PostMapping("/employees")
    @Operation(summary = "Create a new bank employee account (role = EMPLOYEE)")
    @PreAuthorize("hasAuthority('ADMIN_CREATE')")
    public ResponseEntity<ApiResponse<AdminResponse>> createEmployee(
            @Valid @RequestBody CreateAdminRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        AdminResponse created = adminService.createEmployee(req, actorEmail(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Employee created", created));
    }

    // ─── Roles ────────────────────────────────────────────────────────
    @GetMapping("/roles")
    @Operation(summary = "List all roles with their assigned permissions")
    @PreAuthorize("hasAuthority('ADMIN_READ')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles() {
        return ResponseEntity.ok(ApiResponse.ok("Roles loaded", adminService.listRoles()));
    }

    // ─── Audit Logs ───────────────────────────────────────────────────
    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs — filterable by action, actor, and date range")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.ok("Audit logs loaded",
                adminService.getAuditLogs(page, size, action, actorId, from, to)));
    }

    // ─── Helpers ─────────────────────────────────────────────────────
    private String actorEmail(UserDetails userDetails) {
        return userDetails != null ? userDetails.getUsername() : "SYSTEM";
    }
}
