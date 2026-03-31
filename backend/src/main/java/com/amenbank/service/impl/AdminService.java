package com.amenbank.service.impl;

import com.amenbank.dto.request.CreateAdminRequest;
import com.amenbank.dto.response.*;
import com.amenbank.entity.*;
import com.amenbank.enums.*;
import com.amenbank.exception.*;
import com.amenbank.repository.*;
import com.amenbank.service.AuditService;
import com.amenbank.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final KycRequestRepository kycRequestRepository;
    private final CreditApplicationRepository creditApplicationRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccountRepository accountRepository;
    private final AdminRepository adminRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final EmailService emailService;
    private final CreditService creditService;
    private final AccountService accountService;
    private final TransferService transferService;
    private final TransferRepository transferRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.upload-path:/data/uploads}")
    private String uploadPath;

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ─── Dashboard stats ──────────────────────────────────────────────
    public AdminDashboardResponse getDashboardStats() {
        long totalUsers     = userRepository.count();
        long activeUsers    = userRepository.findByStatus(UserStatus.ACTIVE, PageRequest.of(0, 1)).getTotalElements();
        long suspendedUsers = userRepository.findByStatus(UserStatus.SUSPENDED, PageRequest.of(0, 1)).getTotalElements();
        long pendingKyc     = kycRequestRepository.findByStatusOrderByCreatedAtDesc(
                KycStatus.PENDING, PageRequest.of(0, 1)).getTotalElements();
        long pendingCredits = creditApplicationRepository.findByStatusOrderByCreatedAtDesc(
                CreditStatus.PENDING, PageRequest.of(0, 1)).getTotalElements();
        long totalAccounts  = accountRepository.count();
        long totalAdmins    = adminRepository.findByRole(AdminRole.ADMIN, PageRequest.of(0, 1)).getTotalElements();
        long totalEmployees = adminRepository.findByRole(AdminRole.EMPLOYEE, PageRequest.of(0, 1)).getTotalElements();

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .suspendedUsers(suspendedUsers)
                .pendingKycRequests(pendingKyc)
                .pendingCreditApplications(pendingCredits)
                .totalAccounts(totalAccounts)
                .totalAdmins(totalAdmins)
                .totalEmployees(totalEmployees)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ─── User management ─────────────────────────────────────────────
    public PageResponse<UserResponse> listUsers(int page, int size, String search, UserStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage;

        if (search != null && !search.isBlank()) {
            userPage = (status != null)
                    ? userRepository.searchByStatusAndKeyword(status, search, pageable)
                    : userRepository.searchByKeyword(search, pageable);
        } else {
            userPage = (status != null)
                    ? userRepository.findByStatus(status, pageable)
                    : userRepository.findAll(pageable);
        }

        return toPageResponse(userPage.map(this::toUserResponse));
    }

    public UserResponse getUser(Long id) {
        return toUserResponse(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }

    @Transactional
    public void updateUserStatus(Long userId, UserStatus newStatus, String reason, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        UserStatus old = user.getStatus();

        if (old == newStatus) {
            throw new BusinessException("User already has status: " + newStatus, "STATUS_UNCHANGED");
        }

        user.setStatus(newStatus);
        if (newStatus == UserStatus.DELETED) {
            user.setDeletedAt(LocalDateTime.now());
        }
        userRepository.save(user);

        auditService.log("USER_STATUS_UPDATED", "User", userId,
                adminEmail != null ? adminEmail : "SYSTEM",
                old + " → " + newStatus + (reason != null ? " | Reason: " + reason : ""));

        emailService.sendGeneric(user.getEmail(), "Mise à jour du statut de votre compte",
                "Votre compte a été mis à jour : " + newStatus +
                (reason != null ? "\nMotif : " + reason : ""));
    }

    // Keep backward-compatible 3-arg overload used by existing code
    @Transactional
    public void updateUserStatus(Long userId, UserStatus newStatus, String reason) {
        updateUserStatus(userId, newStatus, reason, null);
    }

    @Transactional
    public void resetUserPassword(Long userId, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        // Revoke all active sessions — user must log in again with new password
        refreshTokenRepository.revokeAllByUserId(userId);

        emailService.sendGeneric(user.getEmail(), "Réinitialisation de votre mot de passe",
                "Votre mot de passe a été réinitialisé par un administrateur.\n" +
                "Mot de passe temporaire : " + tempPassword + "\n" +
                "Veuillez le modifier dès votre prochaine connexion.");

        auditService.log("USER_PASSWORD_RESET", "User", userId,
                adminEmail != null ? adminEmail : "SYSTEM",
                "Password reset by admin for user: " + user.getEmail());
    }

    // ─── KYC ─────────────────────────────────────────────────────────
    public PageResponse<KycRequestResponse> listKycRequests(int page, int size, KycStatus status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<KycRequest> p = (status != null)
                ? kycRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : kycRequestRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return toPageResponse(p.map(this::toKycResponse));
    }

    public KycRequestResponse getKycRequest(Long id) {
        return toKycResponse(kycRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KYC request", id)));
    }

    @Transactional
    public void approveKyc(Long kycId, String adminEmail) {
        KycRequest kyc = kycRequestRepository.findById(kycId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC request", kycId));
        String actor = (adminEmail != null && !adminEmail.isBlank()) ? adminEmail : "SYSTEM";

        if (kyc.getStatus() != KycStatus.PENDING && kyc.getStatus() != KycStatus.REVIEWING) {
            throw new BusinessException("KYC request has already been processed", "KYC_ALREADY_PROCESSED");
        }

        kyc.setStatus(KycStatus.APPROVED);
        kyc.setReviewedAt(LocalDateTime.now());
        kycRequestRepository.save(kyc);

        User user = kyc.getUser();
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        accountService.createAccount(user.getId(), AccountType.CHECKING);

        emailService.sendKycStatusUpdate(user.getEmail(), user.getFullName(), "APPROVED", null);
        auditService.log("KYC_APPROVED", "KycRequest", kycId, actor,
                "KYC approved for user: " + user.getEmail());
    }

    @Transactional
    public void rejectKyc(Long kycId, String reason, String adminEmail) {
        KycRequest kyc = kycRequestRepository.findById(kycId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC request", kycId));

        if (kyc.getStatus() != KycStatus.PENDING && kyc.getStatus() != KycStatus.REVIEWING) {
            throw new BusinessException("KYC request has already been processed", "KYC_ALREADY_PROCESSED");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Rejection reason is required", "REJECTION_REASON_REQUIRED");
        }

        kyc.setStatus(KycStatus.REJECTED);
        kyc.setRejectionReason(reason);
        kyc.setReviewedAt(LocalDateTime.now());
        kycRequestRepository.save(kyc);

        User user = kyc.getUser();
        emailService.sendKycStatusUpdate(user.getEmail(), user.getFullName(), "REJECTED", reason);
        auditService.log("KYC_REJECTED", "KycRequest", kycId,
                adminEmail != null ? adminEmail : "SYSTEM",
                "KYC rejected: " + reason);
    }

    // Keep backward-compatible 2-arg overload
    @Transactional
    public void rejectKyc(Long kycId, String reason) {
        rejectKyc(kycId, reason, null);
    }

    @Transactional
    public void uploadKycDocument(Long kycId, String docType, MultipartFile file) {
        KycRequest kyc = kycRequestRepository.findById(kycId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC request", kycId));

        if (docType == null || docType.isBlank()) {
            throw new BusinessException("Document type is required", "INVALID_DOCUMENT_TYPE");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Uploaded file is empty", "EMPTY_FILE");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
                ext = originalFilename.substring(dotIndex);
            }
        }
        String fileName = UUID.randomUUID() + ext;
        Path target = Paths.get(uploadPath, "kyc", kycId.toString(), fileName);

        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Failed to store document: " + e.getMessage(), "FILE_UPLOAD_ERROR");
        }

        com.amenbank.entity.KycDocument doc = com.amenbank.entity.KycDocument.builder()
                .kycRequest(kyc)
                .docType(docType)
                .filePath(target.toString())
                .fileName(originalFilename != null ? originalFilename : fileName)
                .mimeType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .build();

        kyc.getDocuments().add(doc);
        kyc.setStatus(KycStatus.REVIEWING);
        kycRequestRepository.save(kyc);
    }

    // ─── Credits ─────────────────────────────────────────────────────
    public PageResponse<CreditApplicationResponse> listCreditApplications(
            int page, int size, CreditStatus status) {
        var p = (status != null)
                ? creditApplicationRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size))
                : creditApplicationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

        return toPageResponse(p.map(creditService::toCreditResponse));
    }

    @Transactional
    public CreditApplicationResponse approveCredit(Long id, String reviewerEmail) {
        return creditService.approveCredit(id, reviewerEmail);
    }

    @Transactional
    public CreditApplicationResponse rejectCredit(Long id, String reason, String reviewerEmail) {
        return creditService.rejectCredit(id, reason, reviewerEmail);
    }

    // ─── Audit logs ───────────────────────────────────────────────────
    public PageResponse<AuditLogResponse> getAuditLogs(
            int page, int size, String action, Long actorId,
            LocalDateTime from, LocalDateTime to) {
        var p = auditLogRepository.search(null, actorId, action, from, to, PageRequest.of(page, size));
        return toPageResponse(p.map(a -> AuditLogResponse.builder()
                .id(a.getId())
                .actorType(a.getActorType().name())
                .actorEmail(a.getActorEmail())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .description(a.getDescription())
                .ipAddress(a.getIpAddress())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .build()));
    }

    // ─── Admin management ─────────────────────────────────────────────
    public PageResponse<AdminResponse> listAdmins(int page, int size) {
        Page<Admin> p = adminRepository.findByRole(
                AdminRole.ADMIN, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return toPageResponse(p.map(this::toAdminResponse));
    }

    public PageResponse<AdminResponse> listEmployees(int page, int size) {
        Page<Admin> p = adminRepository.findByRole(
                AdminRole.EMPLOYEE, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return toPageResponse(p.map(this::toAdminResponse));
    }

    public AdminResponse getAdmin(Long id) {
        return toAdminResponse(adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", id)));
    }

    @Transactional
    public AdminResponse createAdmin(CreateAdminRequest req, String createdByEmail) {
        return createStaffAccount(req, AdminRole.ADMIN, createdByEmail);
    }

    @Transactional
    public AdminResponse createEmployee(CreateAdminRequest req, String createdByEmail) {
        // Employees can only be created with EMPLOYEE role — ignore role field in request
        return createStaffAccount(req, AdminRole.EMPLOYEE, createdByEmail);
    }

    @Transactional
    public AdminResponse updateAdminRole(Long id, AdminRole newRole, String updatedByEmail) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", id));
        AdminRole old = admin.getRole();
        if (old == newRole) {
            throw new BusinessException("Role is already " + newRole, "ROLE_UNCHANGED");
        }
        admin.setRole(newRole);
        adminRepository.save(admin);
        auditService.log("ADMIN_ROLE_UPDATED", "Admin", id, updatedByEmail,
                "Role changed: " + old + " → " + newRole);
        return toAdminResponse(admin);
    }

    @Transactional
    public AdminResponse toggleAdminStatus(Long id, boolean active, String updatedByEmail) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", id));
        admin.setActive(active);
        adminRepository.save(admin);
        auditService.log(active ? "ADMIN_ENABLED" : "ADMIN_DISABLED", "Admin", id, updatedByEmail,
                "Admin " + admin.getEmail() + " " + (active ? "enabled" : "disabled"));
        return toAdminResponse(admin);
    }

    // ─── Role management ──────────────────────────────────────────────
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll(Sort.by("name")).stream()
                .map(r -> RoleResponse.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .permissions(r.getPermissions().stream()
                                .map(p -> RoleResponse.PermissionResponse.builder()
                                        .id(p.getId())
                                        .name(p.getName())
                                        .description(p.getDescription())
                                        .build())
                                .sorted(Comparator.comparing(RoleResponse.PermissionResponse::getName))
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  EMPLOYEE OPERATIONS — transfer validation, deposits, withdrawals
    // ═══════════════════════════════════════════════════════════════════

    // ─── View client accounts (read-only, any account) ───────────────
    public List<AccountResponse> getClientAccounts(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return accountRepository.findByUserId(userId).stream()
                .map(accountService::toAccountResponse)
                .collect(Collectors.toList());
    }

    public PageResponse<TransactionResponse> getAccountTransactions(
            Long accountId, int page, int size) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        Page<Transaction> txPage = transactionRepository.findByAccountIdOrderByCreatedAtDesc(
                accountId, PageRequest.of(page, size));
        return toPageResponse(txPage.map(accountService::toTxResponse));
    }

    // ─── Transfer validation ─────────────────────────────────────────
    public PageResponse<TransferResponse> listAllTransfers(int page, int size, TransferStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Transfer> p = (status != null)
                ? transferRepository.findByStatus(status, pageable)
                : transferRepository.findAll(pageable);

        return toPageResponse(p.map(t -> {
            TransferResponse r = transferService.toTransferResponse(t);
            if (t.getFromAccount() != null && t.getFromAccount().getUser() != null) {
                r.setClientName(t.getFromAccount().getUser().getFullName());
                r.setClientEmail(t.getFromAccount().getUser().getEmail());
            }
            return r;
        }));
    }

    @Transactional
    public TransferResponse approveTransfer(Long transferId, String employeeEmail) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new BusinessException("Only pending transfers can be approved", "NOT_PENDING");
        }

        User user = transfer.getFromAccount().getUser();
        TransferResponse response = transferService.executeTransfer(transfer, user);

        auditService.log("TRANSFER_APPROVED", "Transfer", transferId, employeeEmail,
                "Transfer of " + transfer.getAmount() + " TND approved for " + user.getEmail());
        return response;
    }

    @Transactional
    public TransferResponse rejectTransfer(Long transferId, String reason, String employeeEmail) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new BusinessException("Only pending transfers can be rejected", "NOT_PENDING");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Rejection reason is required", "REJECTION_REASON_REQUIRED");
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setFailureReason(reason);
        transferRepository.save(transfer);

        User user = transfer.getFromAccount().getUser();
        emailService.sendGeneric(user.getEmail(), "Virement refusé — Amen Bank",
                "Votre virement de " + transfer.getAmount() + " TND vers " + transfer.getToName() +
                " a été refusé.\nMotif : " + reason);

        auditService.log("TRANSFER_REJECTED", "Transfer", transferId, employeeEmail,
                "Transfer rejected for " + user.getEmail() + ": " + reason);

        return transferService.toTransferResponse(transfer);
    }

    // ─── Cash operations: deposit & withdrawal ───────────────────────
    @Transactional
    public TransactionResponse deposit(Long accountId, BigDecimal amount,
                                        String description, String employeeEmail) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Account is not active", "ACCOUNT_NOT_ACTIVE");
        }

        Transaction tx = accountService.creditAccount(account, amount,
                null, "Dépôt au guichet",
                TransactionCategory.DEPOSIT,
                description != null && !description.isBlank() ? description : "Dépôt espèces au guichet");

        auditService.log("CASH_DEPOSIT", "Account", accountId, employeeEmail,
                "Deposit of " + amount + " TND into account " + account.getAccountNumber());

        return accountService.toTxResponse(tx);
    }

    @Transactional
    public TransactionResponse withdraw(Long accountId, BigDecimal amount,
                                         String description, String employeeEmail) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Account is not active", "ACCOUNT_NOT_ACTIVE");
        }

        Transaction tx = accountService.debitAccount(account, amount,
                null, "Retrait au guichet",
                TransactionCategory.WITHDRAWAL,
                description != null && !description.isBlank() ? description : "Retrait espèces au guichet");

        auditService.log("CASH_WITHDRAWAL", "Account", accountId, employeeEmail,
                "Withdrawal of " + amount + " TND from account " + account.getAccountNumber());

        return accountService.toTxResponse(tx);
    }

    // ─── Private helpers ─────────────────────────────────────────────
    private AdminResponse createStaffAccount(CreateAdminRequest req, AdminRole role, String createdByEmail) {
        if (adminRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("Email already in use", "EMAIL_ALREADY_EXISTS");
        }
        if (adminRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException("Username already in use", "USERNAME_ALREADY_EXISTS");
        }

        Admin admin = Admin.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .role(role)
                .active(true)
                .totpEnabled(false)
                .build();

        adminRepository.save(admin);
        auditService.log("ADMIN_CREATED", "Admin", admin.getId(), createdByEmail,
                "Staff account created: " + admin.getEmail() + " with role " + role);
        emailService.sendGeneric(admin.getEmail(), "Compte " + role.name().toLowerCase() + " créé — Amen Bank",
                "Votre compte a été créé.\n" +
                "Identifiant : " + admin.getUsername() + "\n" +
                "Rôle : " + role + "\n" +
                "Veuillez vous connecter et configurer votre authentification à deux facteurs.");
        return toAdminResponse(admin);
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    // ─── Mappers ─────────────────────────────────────────────────────
    private UserResponse toUserResponse(User u) {
        return UserResponse.builder()
                .id(u.getId()).username(u.getUsername()).email(u.getEmail())
                .firstName(u.getFirstName()).lastName(u.getLastName())
                .phoneNumber(u.getPhoneNumber()).status(u.getStatus())
                .totpEnabled(u.getTotpEnabled()).emailVerified(u.getEmailVerified())
                .lastLoginAt(u.getLastLoginAt()).createdAt(u.getCreatedAt())
                .roles(u.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList()))
                .build();
    }

    AdminResponse toAdminResponse(Admin a) {
        return AdminResponse.builder()
                .id(a.getId())
                .username(a.getUsername())
                .email(a.getEmail())
                .firstName(a.getFirstName())
                .lastName(a.getLastName())
                .role(a.getRole())
                .totpEnabled(a.getTotpEnabled())
                .active(a.getActive())
                .lastLoginAt(a.getLastLoginAt())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private KycRequestResponse toKycResponse(KycRequest k) {
        return KycRequestResponse.builder()
                .id(k.getId())
                .userId(k.getUser().getId())
                .userFullName(k.getUser().getFullName())
                .userEmail(k.getUser().getEmail())
                .status(k.getStatus())
                .rejectionReason(k.getRejectionReason())
                .createdAt(k.getCreatedAt())
                .reviewedAt(k.getReviewedAt())
                .documentCount(k.getDocuments().size())
                .build();
    }

    private <T> PageResponse<T> toPageResponse(Page<T> p) {
        return PageResponse.<T>builder()
                .content(p.getContent())
                .page(p.getNumber()).size(p.getSize())
                .totalElements(p.getTotalElements()).totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .build();
    }
}
