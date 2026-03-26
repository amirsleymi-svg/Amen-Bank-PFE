package com.amenbank.service.impl;

import com.amenbank.dto.response.*;
import com.amenbank.entity.KycRequest;
import com.amenbank.entity.User;
import com.amenbank.enums.*;
import com.amenbank.exception.*;
import com.amenbank.repository.*;
import com.amenbank.service.AuditService;
import com.amenbank.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
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
    private final AuditService auditService;
    private final EmailService emailService;
    private final CreditService creditService;
    private final AccountService accountService;

    @Value("${app.upload-path:/data/uploads}")
    private String uploadPath;

    // ─── Dashboard stats ──────────────────────────────────────────────
    public AdminDashboardResponse getDashboardStats() {
        long totalUsers       = userRepository.count();
        long pendingKyc       = kycRequestRepository.findByStatusOrderByCreatedAtDesc(
                KycStatus.PENDING, PageRequest.of(0, 1)).getTotalElements();
        long pendingCredits   = creditApplicationRepository.findByStatusOrderByCreatedAtDesc(
                CreditStatus.PENDING, PageRequest.of(0, 1)).getTotalElements();
        long totalAccounts    = accountRepository.count();

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .pendingKycRequests(pendingKyc)
                .pendingCreditApplications(pendingCredits)
                .totalAccounts(totalAccounts)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ─── List users ───────────────────────────────────────────────────
    public PageResponse<UserResponse> listUsers(int page, int size, String search, UserStatus status) {
        UserStatus effectiveStatus = status != null ? status : UserStatus.ACTIVE;
        Page<User> userPage = (search != null && !search.isBlank())
                ? userRepository.searchByStatusAndKeyword(effectiveStatus, search, PageRequest.of(page, size))
                : userRepository.findByStatus(effectiveStatus, PageRequest.of(page, size));

        return PageResponse.<UserResponse>builder()
                .content(userPage.getContent().stream().map(this::toUserResponse).collect(Collectors.toList()))
                .page(userPage.getNumber()).size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst()).last(userPage.isLast())
                .build();
    }

    public UserResponse getUser(Long id) {
        return toUserResponse(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }

    @Transactional
    public void updateUserStatus(Long userId, UserStatus newStatus, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        UserStatus old = user.getStatus();
        user.setStatus(newStatus);
        userRepository.save(user);

        auditService.log("USER_STATUS_UPDATED", "User", userId, user.getEmail(),
                old + " → " + newStatus + (reason != null ? " Reason: " + reason : ""));

        emailService.sendGeneric(user.getEmail(), "Account Status Update",
                "Your account status has been updated to: " + newStatus +
                (reason != null ? "\nReason: " + reason : ""));
    }

    // ─── KYC ─────────────────────────────────────────────────────────
    public PageResponse<KycRequestResponse> listKycRequests(int page, int size, KycStatus status) {
        KycStatus eff = status != null ? status : KycStatus.PENDING;
        Page<KycRequest> p = kycRequestRepository.findByStatusOrderByCreatedAtDesc(
                eff, PageRequest.of(page, size));
        return PageResponse.<KycRequestResponse>builder()
                .content(p.getContent().stream().map(this::toKycResponse).collect(Collectors.toList()))
                .page(p.getNumber()).size(p.getSize())
                .totalElements(p.getTotalElements()).totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .build();
    }

    public KycRequestResponse getKycRequest(Long id) {
        return toKycResponse(kycRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KYC request", id)));
    }

    @Transactional
    public void approveKyc(Long kycId, String adminEmail) {
        KycRequest kyc = kycRequestRepository.findById(kycId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC request", kycId));

        kyc.setStatus(KycStatus.APPROVED);
        kyc.setReviewedAt(LocalDateTime.now());
        kycRequestRepository.save(kyc);

        // Activate user account + create default checking account
        User user = kyc.getUser();
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        accountService.createAccount(user.getId(), AccountType.CHECKING);

        emailService.sendKycStatusUpdate(user.getEmail(), user.getFullName(), "APPROVED", null);
        auditService.log("KYC_APPROVED", "KycRequest", kycId, adminEmail,
                "KYC approved for user: " + user.getEmail());
    }

    @Transactional
    public void rejectKyc(Long kycId, String reason) {
        KycRequest kyc = kycRequestRepository.findById(kycId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC request", kycId));

        kyc.setStatus(KycStatus.REJECTED);
        kyc.setRejectionReason(reason);
        kyc.setReviewedAt(LocalDateTime.now());
        kycRequestRepository.save(kyc);

        User user = kyc.getUser();
        emailService.sendKycStatusUpdate(user.getEmail(), user.getFullName(), "REJECTED", reason);
        auditService.log("KYC_REJECTED", "KycRequest", kycId, null,
                "KYC rejected: " + reason);
    }

    @Transactional
    public void uploadKycDocument(Long kycId, String docType, MultipartFile file) {
        KycRequest kyc = kycRequestRepository.findById(kycId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC request", kycId));

        String ext = Objects.requireNonNull(file.getOriginalFilename())
                .substring(file.getOriginalFilename().lastIndexOf('.'));
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
                .fileName(file.getOriginalFilename())
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
        var p = status != null
                ? creditApplicationRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size))
                : creditApplicationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

        return PageResponse.<CreditApplicationResponse>builder()
                .content(p.getContent().stream().map(creditService::toCreditResponse).collect(Collectors.toList()))
                .page(p.getNumber()).size(p.getSize())
                .totalElements(p.getTotalElements()).totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .build();
    }

    public CreditApplicationResponse updateCreditStatus(Long id, CreditStatus status, String reason) {
        return creditService.updateStatus(id, status, reason, null);
    }

    // ─── Audit logs ───────────────────────────────────────────────────
    public PageResponse<AuditLogResponse> getAuditLogs(int page, int size, String action, Long actorId) {
        var p = auditLogRepository.search(null, actorId, action, null, null, PageRequest.of(page, size));
        return PageResponse.<AuditLogResponse>builder()
                .content(p.getContent().stream().map(a -> AuditLogResponse.builder()
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
                        .build()).collect(Collectors.toList()))
                .page(p.getNumber()).size(p.getSize())
                .totalElements(p.getTotalElements()).totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .build();
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
}
