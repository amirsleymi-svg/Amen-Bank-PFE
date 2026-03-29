package com.amenbank.service.impl;

import com.amenbank.dto.request.ActivateAccountRequest;
import com.amenbank.dto.request.CreateAccountFromRequestDto;
import com.amenbank.dto.request.RegistrationRequestDto;
import com.amenbank.dto.response.PageResponse;
import com.amenbank.dto.response.RegistrationRequestResponse;
import com.amenbank.dto.response.UserResponse;
import com.amenbank.entity.ActivationToken;
import com.amenbank.entity.RegistrationRequest;
import com.amenbank.entity.Role;
import com.amenbank.entity.User;
import com.amenbank.enums.RegistrationStatus;
import com.amenbank.enums.UserStatus;
import com.amenbank.exception.BusinessException;
import com.amenbank.exception.DuplicateResourceException;
import com.amenbank.exception.ResourceNotFoundException;
import com.amenbank.repository.ActivationTokenRepository;
import com.amenbank.repository.RegistrationRequestRepository;
import com.amenbank.repository.RoleRepository;
import com.amenbank.repository.UserRepository;
import com.amenbank.service.AuditService;
import com.amenbank.service.EmailService;
import com.amenbank.service.OnboardingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OnboardingServiceImpl implements OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingServiceImpl.class);

    private final RegistrationRequestRepository registrationRequestRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${app.activation-token-hours:48}")
    private int activationTokenHours;

    public OnboardingServiceImpl(
            RegistrationRequestRepository registrationRequestRepository,
            ActivationTokenRepository activationTokenRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuditService auditService) {
        this.registrationRequestRepository = registrationRequestRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    // ─── SUBMIT REGISTRATION REQUEST ──────────────────────────────────
    @Override
    @Transactional
    public RegistrationRequestResponse submitRegistrationRequest(RegistrationRequestDto dto, String ipAddress) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase(Locale.ROOT);
        String normalizedConfirmEmail = dto.getConfirmEmail().trim().toLowerCase(Locale.ROOT);

        // Validate emails match
        if (!normalizedEmail.equals(normalizedConfirmEmail)) {
            throw new BusinessException("Email addresses do not match", "EMAIL_MISMATCH");
        }

        // Check for duplicate request
        if (registrationRequestRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("A registration request for this email already exists: " + normalizedEmail);
        }

        // Check if already a user
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("This email is already registered");
        }

        RegistrationRequest request = RegistrationRequest.builder()
                .email(normalizedEmail)
                .status(RegistrationStatus.PENDING)
                .ipAddress(ipAddress)
                .build();

        RegistrationRequest saved = registrationRequestRepository.save(request);

        // Notify admins
        emailService.notifyAdminsNewRegistrationRequest(normalizedEmail);

        auditService.log("REGISTRATION_REQUEST_SUBMITTED", "RegistrationRequest",
                saved.getId(), normalizedEmail, "New onboarding request from " + ipAddress);

        log.info("New registration request: {} from {}", normalizedEmail, ipAddress);
        return toResponse(saved);
    }

    // ─── LIST REGISTRATION REQUESTS ───────────────────────────────────
    @Override
    public PageResponse<RegistrationRequestResponse> listRegistrationRequests(
            int page, int size, RegistrationStatus status) {

        Page<RegistrationRequest> p = (status != null)
                ? registrationRequestRepository.findByStatusOrderByCreatedAtDesc(status, buildPageRequest(page, size))
                : registrationRequestRepository.findAllByOrderByCreatedAtDesc(buildPageRequest(page, size));

        return PageResponse.<RegistrationRequestResponse>builder()
                .content(p.getContent().stream().map(this::toResponse).collect(Collectors.toList()))
                .page(p.getNumber()).size(p.getSize())
                .totalElements(p.getTotalElements()).totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .build();
    }

    // ─── CREATE ACCOUNT FROM REQUEST ──────────────────────────────────
    @Override
    @Transactional
    public UserResponse createAccountFromRequest(CreateAccountFromRequestDto dto, String adminEmail) {
        // Load and validate the request
        RegistrationRequest request = registrationRequestRepository.findById(dto.getRegistrationRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("RegistrationRequest", dto.getRegistrationRequestId()));

        if (request.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessException("This registration request has already been processed", "REQUEST_ALREADY_PROCESSED");
        }

        String email = request.getEmail();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("This email is already registered");
        }

        // Build username from email prefix
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_.-]", "_");
        String username = baseUsername;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + suffix++;
        }

        // Create user account (disabled — no password yet, random placeholder)
        String tempHash = passwordEncoder.encode(UUID.randomUUID().toString());

        // Assign ROLE_CLIENT
        Role clientRole = roleRepository.findByName("ROLE_CLIENT")
                .orElseGet(() -> roleRepository.findByName("ROLE_USER")
                        .orElseThrow(() -> new ResourceNotFoundException("Default role not configured")));

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(tempHash)
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .phoneNumber(dto.getPhoneNumber())
                .dateOfBirth(dto.getDateOfBirth())
                .address(dto.getAddress())
                .idCardNumber("PENDING-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status(UserStatus.PENDING)   // enabled=false until activation
                .roles(Set.of(clientRole))
                .build();

        User saved = userRepository.save(user);

        // Generate activation token
        String tokenValue = UUID.randomUUID().toString();
        activationTokenRepository.invalidateAllByUserId(saved.getId());
        ActivationToken activationToken = ActivationToken.builder()
                .user(saved)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusHours(activationTokenHours))
                .build();
        activationTokenRepository.save(activationToken);

        // Update request status
        request.setStatus(RegistrationStatus.APPROVED);
        request.setReviewedBy(adminEmail);
        request.setReviewedAt(LocalDateTime.now());
        registrationRequestRepository.save(request);

        // Send activation email
        String fullName = (dto.getFirstName().trim() + " " + dto.getLastName().trim()).trim();
        emailService.sendAccountActivation(email, fullName, tokenValue);

        auditService.log("ACCOUNT_CREATED_BY_ADMIN", "User", saved.getId(), adminEmail,
                "Account created for " + email + " from registration request #" + request.getId());

        log.info("Admin {} created account for {} (request #{})", adminEmail, email, request.getId());

        return toUserResponse(saved);
    }

    // ─── REJECT REGISTRATION REQUEST ──────────────────────────────────
    @Override
    @Transactional
    public void rejectRegistrationRequest(Long requestId, String reason, String adminEmail) {
        RegistrationRequest request = registrationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("RegistrationRequest", requestId));

        if (request.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessException("This registration request has already been processed", "REQUEST_ALREADY_PROCESSED");
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Rejection reason is required", "REJECTION_REASON_REQUIRED");
        }

        request.setStatus(RegistrationStatus.REJECTED);
        request.setRejectionReason(reason.trim());
        request.setReviewedBy(adminEmail);
        request.setReviewedAt(LocalDateTime.now());
        registrationRequestRepository.save(request);

        // Notify applicant
        emailService.sendRegistrationRejected(request.getEmail(), reason.trim());

        auditService.log("REGISTRATION_REQUEST_REJECTED", "RegistrationRequest",
                requestId, adminEmail, "Rejected: " + reason);

        log.info("Admin {} rejected registration request #{}: {}", adminEmail, requestId, reason);
    }

    // ─── ACTIVATE ACCOUNT ─────────────────────────────────────────────
    @Override
    @Transactional
    public void activateAccount(ActivateAccountRequest request) {
        // Validate passwords match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match", "PASSWORD_MISMATCH");
        }

        // Find and validate token
        ActivationToken token = activationTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BusinessException("Invalid activation link", "INVALID_TOKEN"));

        if (!token.isValid()) {
            throw new BusinessException(
                    token.getUsed() ? "This activation link has already been used"
                                    : "This activation link has expired. Please contact the bank.",
                    "TOKEN_INVALID"
            );
        }

        // Activate user
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        userRepository.save(user);

        // Invalidate token
        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        activationTokenRepository.save(token);

        emailService.sendAccountActivatedConfirmation(user.getEmail(), user.getFirstName() + " " + user.getLastName());

        auditService.log("ACCOUNT_ACTIVATED", "User", user.getId(), user.getEmail(),
                "Account activated via email link");

        log.info("Account activated for user: {}", user.getEmail());
    }

    // ─── Mappers ──────────────────────────────────────────────────────
    private RegistrationRequestResponse toResponse(RegistrationRequest r) {
        return RegistrationRequestResponse.builder()
                .id(r.getId())
                .email(r.getEmail())
                .status(r.getStatus())
                .rejectionReason(r.getRejectionReason())
                .reviewedBy(r.getReviewedBy())
                .reviewedAt(r.getReviewedAt())
                .ipAddress(r.getIpAddress())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private UserResponse toUserResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .phoneNumber(u.getPhoneNumber())
                .status(u.getStatus())
                .totpEnabled(u.getTotpEnabled())
                .emailVerified(u.getEmailVerified())
                .createdAt(u.getCreatedAt())
                .roles(u.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .build();
    }

    private PageRequest buildPageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(safePage, safeSize);
    }
}
