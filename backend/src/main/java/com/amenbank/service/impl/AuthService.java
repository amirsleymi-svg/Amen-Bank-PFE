package com.amenbank.service.impl;

import com.amenbank.dto.request.*;
import com.amenbank.dto.response.AdminResponse;
import com.amenbank.dto.response.AuthResponse;
import com.amenbank.dto.response.TotpSetupResponse;
import com.amenbank.dto.response.UserResponse;
import com.amenbank.entity.*;
import com.amenbank.enums.UserStatus;
import com.amenbank.exception.*;
import com.amenbank.repository.*;
import com.amenbank.security.jwt.JwtUtils;
import com.amenbank.security.totp.TotpService;
import com.amenbank.service.AuditService;
import com.amenbank.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final KycRequestRepository kycRequestRepository;
    private final JwtUtils jwtUtils;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${app.lockout-duration-minutes:15}")
    private int lockoutMinutes;

    // Redis key prefixes
    private static final String TEMP_TOKEN_PREFIX    = "temp_token:";
    private static final String SETUP_TOKEN_PREFIX   = "setup_token:";
    private static final String TOTP_ATTEMPTS_PREFIX = "totp_attempts:";
    // Admin principal prefix stored inside Redis values: "ADMIN:{adminId}" vs just "{userId}"
    private static final String ADMIN_PRINCIPAL_PREFIX = "ADMIN:";

    private static final int MAX_TOTP_ATTEMPTS = 5;

    // Alphanumeric without ambiguous chars (0/O, 1/I/L)
    private static final String BACKUP_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ─── REGISTER (admin creates client account) ──────────────────────
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByIdCardNumber(request.getIdCardNumber())) {
            throw new DuplicateResourceException("ID card number already registered");
        }

        Optional<IdentityVerification> identity =
                identityVerificationRepository.findByIdCardNumber(request.getIdCardNumber());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .idCardNumber(request.getIdCardNumber())
                .dateOfBirth(request.getDateOfBirth())
                .address(request.getAddress())
                .status(UserStatus.PENDING)
                .emailToken(UUID.randomUUID().toString())
                .emailTokenExpires(LocalDateTime.now().plusHours(24))
                .build();

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("Default role not configured"));
        user.setRoles(Set.of(userRole));

        User saved = userRepository.save(user);

        KycRequest kycRequest = KycRequest.builder()
                .user(saved)
                .build();
        kycRequest.setStatus(com.amenbank.enums.KycStatus.PENDING);
        kycRequestRepository.save(kycRequest);

        emailService.sendEmailVerification(saved.getEmail(), saved.getFullName(), saved.getEmailToken());
        emailService.notifyAdminsNewKycRequest(saved.getFullName(), saved.getEmail());
        auditService.log("USER_REGISTERED", "User", saved.getId(), saved.getEmail(),
                "New user registration - KYC pending");

        log.info("New user registered: {} ({})", saved.getUsername(), saved.getEmail());
        return toUserResponse(saved);
    }

    // ─── LOGIN ────────────────────────────────────────────────────────
    @Transactional(noRollbackFor = {
        BadCredentialsException.class,
        LockedException.class,
        DisabledException.class
    })
    public AuthResponse login(LoginRequest request, String ipAddress, String deviceInfo) {
        String identifier = request.getIdentifier();

        // Check user table first
        Optional<User> userOpt = userRepository.findByEmailOrUsername(identifier);
        if (userOpt.isPresent()) {
            return loginUser(userOpt.get(), request, ipAddress, deviceInfo);
        }

        // Fall back to admin/employee table
        Optional<Admin> adminOpt = adminRepository.findByEmailOrUsername(identifier);
        if (adminOpt.isPresent()) {
            return loginAdmin(adminOpt.get(), request, ipAddress, deviceInfo);
        }

        throw new BadCredentialsException("Invalid credentials");
    }

    private AuthResponse loginUser(User user, LoginRequest request, String ipAddress, String deviceInfo) {
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account is temporarily locked. Try again later.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            handleFailedUserLogin(user);
            throw e;
        }

        userRepository.resetLoginAttempts(user.getId());

        if (!user.isActive()) {
            throw new DisabledException("Account is not active");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);

        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            String tempToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    TEMP_TOKEN_PREFIX + tempToken,
                    String.valueOf(user.getId()),
                    5, TimeUnit.MINUTES
            );
            auditService.log("LOGIN_2FA_PENDING", "User", user.getId(), user.getEmail(), null);
            return AuthResponse.builder()
                    .totpRequired(true)
                    .totpSetupRequired(false)
                    .tempToken(tempToken)
                    .actorType("USER")
                    .build();
        } else {
            String setupToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    SETUP_TOKEN_PREFIX + setupToken,
                    String.valueOf(user.getId()),
                    10, TimeUnit.MINUTES
            );
            auditService.log("LOGIN_2FA_SETUP_REQUIRED", "User", user.getId(), user.getEmail(),
                    "Mandatory 2FA enrollment required");
            return AuthResponse.builder()
                    .totpRequired(false)
                    .totpSetupRequired(true)
                    .tempToken(setupToken)
                    .actorType("USER")
                    .build();
        }
    }

    private AuthResponse loginAdmin(Admin admin, LoginRequest request, String ipAddress, String deviceInfo) {
        if (!admin.getActive()) {
            throw new DisabledException("Administrator account is disabled");
        }
        if (!admin.isAccountNonLocked()) {
            throw new LockedException("Account is temporarily locked. Try again later.");
        }

        // Verify password directly (admin has no KYC/email-verification flow)
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            handleFailedAdminLogin(admin);
            throw new BadCredentialsException("Invalid credentials");
        }

        adminRepository.resetLoginAttempts(admin.getId());

        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);

        String actorType = admin.getRole().name(); // "ADMIN" or "EMPLOYEE"

        if (Boolean.TRUE.equals(admin.getTotpEnabled())) {
            String tempToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    TEMP_TOKEN_PREFIX + tempToken,
                    ADMIN_PRINCIPAL_PREFIX + admin.getId(),
                    5, TimeUnit.MINUTES
            );
            auditService.log("LOGIN_2FA_PENDING", "Admin", admin.getId(), admin.getEmail(), null);
            return AuthResponse.builder()
                    .totpRequired(true)
                    .totpSetupRequired(false)
                    .tempToken(tempToken)
                    .actorType(actorType)
                    .build();
        } else {
            // Admins must also configure 2FA before full access
            String setupToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    SETUP_TOKEN_PREFIX + setupToken,
                    ADMIN_PRINCIPAL_PREFIX + admin.getId(),
                    10, TimeUnit.MINUTES
            );
            auditService.log("LOGIN_2FA_SETUP_REQUIRED", "Admin", admin.getId(), admin.getEmail(),
                    "Mandatory 2FA enrollment required for admin");
            return AuthResponse.builder()
                    .totpRequired(false)
                    .totpSetupRequired(true)
                    .tempToken(setupToken)
                    .actorType(actorType)
                    .build();
        }
    }

    // ─── VERIFY 2FA ───────────────────────────────────────────────────
    @Transactional
    public AuthResponse verifyTotp(TotpVerifyRequest request, String ipAddress, String deviceInfo) {
        String attemptsKey = TOTP_ATTEMPTS_PREFIX + request.getTempToken();
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = (attemptsStr != null) ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_TOTP_ATTEMPTS) {
            redisTemplate.delete(TEMP_TOKEN_PREFIX + request.getTempToken());
            redisTemplate.delete(attemptsKey);
            throw new UnauthorizedException("Too many failed attempts. Please login again.");
        }

        String principal = redisTemplate.opsForValue().get(TEMP_TOKEN_PREFIX + request.getTempToken());
        if (principal == null) {
            throw new UnauthorizedException("Invalid or expired session. Please login again.");
        }

        boolean valid;
        AuthResponse response;

        if (principal.startsWith(ADMIN_PRINCIPAL_PREFIX)) {
            long adminId = Long.parseLong(principal, ADMIN_PRINCIPAL_PREFIX.length(), principal.length(), 10);
            Admin admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin", adminId));

            valid = totpService.verifyCode(admin.getTotpSecret(), request.getTotpCode());
            if (!valid) {
                incrementTotpAttempts(attemptsKey);
                throw new InvalidTotpException();
            }

            redisTemplate.delete(TEMP_TOKEN_PREFIX + request.getTempToken());
            redisTemplate.delete(attemptsKey);
            auditService.log("ADMIN_LOGIN", "Admin", admin.getId(), admin.getEmail(),
                    "Successful login with 2FA from " + ipAddress);
            response = generateAdminTokenPair(admin, ipAddress, deviceInfo);
        } else {
            long userId = Long.parseLong(principal, 0, principal.length(), 10);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));

            valid = totpService.verifyCode(user.getTotpSecret(), request.getTotpCode());
            if (!valid) {
                valid = verifyBackupCode(user, request.getTotpCode());
            }
            if (!valid) {
                incrementTotpAttempts(attemptsKey);
                throw new InvalidTotpException();
            }

            redisTemplate.delete(TEMP_TOKEN_PREFIX + request.getTempToken());
            redisTemplate.delete(attemptsKey);
            auditService.log("USER_LOGIN", "User", user.getId(), user.getEmail(),
                    "Successful login with 2FA from " + ipAddress);
            response = generateUserTokenPair(user, ipAddress, deviceInfo);
        }

        return response;
    }

    // ─── REFRESH TOKEN ────────────────────────────────────────────────
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress, String deviceInfo) {
        String token = request.getRefreshToken();

        if (!jwtUtils.validateToken(token)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        String tokenHash = hashToken(token);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found or already used"));

        if (!stored.isValid()) {
            if (stored.getUser() != null) {
                refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            } else if (stored.getAdmin() != null) {
                refreshTokenRepository.revokeAllByAdminId(stored.getAdmin().getId());
            }
            throw new UnauthorizedException("Refresh token reuse detected. All sessions invalidated.");
        }

        stored.setRevoked(true);
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        if (stored.getAdmin() != null) {
            return generateAdminTokenPair(stored.getAdmin(), ipAddress, deviceInfo);
        }
        return generateUserTokenPair(stored.getUser(), ipAddress, deviceInfo);
    }

    // ─── LOGOUT ───────────────────────────────────────────────────────
    @Transactional
    public void logout(String refreshToken, Long userId) {
        String tokenHash = hashToken(refreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
            rt.setRevoked(true);
            rt.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(rt);
        });
        auditService.log("USER_LOGOUT", "User", userId, null, "User logged out");
    }

    @Transactional
    public void logoutAdmin(String refreshToken, Long adminId) {
        String tokenHash = hashToken(refreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
            rt.setRevoked(true);
            rt.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(rt);
        });
        auditService.log("ADMIN_LOGOUT", "Admin", adminId, null, "Admin logged out");
    }

    // ─── SETUP TOTP (client) ──────────────────────────────────────────
    @Transactional
    public TotpSetupResponse setupTotp(Long userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException("TOTP is already enabled for this account");
        }

        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);

        return TotpSetupResponse.builder()
                .secret(secret)
                .qrCodeDataUri(totpService.generateQrCodeDataUri(user.getEmail(), secret))
                .otpAuthUri(totpService.generateOtpAuthUri(user.getEmail(), secret))
                .issuer(totpService.getIssuer())
                .digits(totpService.getDigits())
                .period(totpService.getPeriod())
                .build();
    }

    // ─── ENABLE TOTP (client) ─────────────────────────────────────────
    @Transactional
    public List<String> enableTotp(Long userId, String totpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getTotpSecret() == null) {
            throw new BusinessException("TOTP not set up. Call /auth/totp/setup first.");
        }
        if (!totpService.verifyCode(user.getTotpSecret(), totpCode)) {
            throw new InvalidTotpException();
        }

        user.setTotpEnabled(true);
        userRepository.save(user);

        List<String> rawCodes = generateAndSaveBackupCodes(user);
        emailService.send2FAEnabledEmail(user.getEmail(), user.getFullName());
        auditService.log("TOTP_ENABLED", "User", userId, user.getEmail(), "2FA enabled");
        return rawCodes;
    }

    // ─── DISABLE TOTP (client) ────────────────────────────────────────
    @Transactional
    public void disableTotp(Long userId, String totpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException("2FA is not enabled on this account.");
        }
        if (!totpService.verifyCode(user.getTotpSecret(), totpCode)) {
            throw new InvalidTotpException();
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        backupCodeRepository.deleteAllByUserId(userId);
        auditService.log("TOTP_DISABLED", "User", userId, user.getEmail(), "2FA disabled");
    }

    // ─── MANDATORY TOTP SETUP (universal — works for users and admins) ─
    @Transactional
    public TotpSetupResponse mandatorySetupTotp(String setupToken) throws Exception {
        String principal = resolveSetupTokenPrincipal(setupToken);

        if (principal.startsWith(ADMIN_PRINCIPAL_PREFIX)) {
            long adminId = Long.parseLong(principal, ADMIN_PRINCIPAL_PREFIX.length(), principal.length(), 10);
            Admin admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin", adminId));

            if (Boolean.TRUE.equals(admin.getTotpEnabled())) {
                throw new BusinessException("TOTP is already enabled for this account");
            }
            String secret = totpService.generateSecret();
            admin.setTotpSecret(secret);
            adminRepository.save(admin);

            return TotpSetupResponse.builder()
                    .secret(secret)
                    .qrCodeDataUri(totpService.generateQrCodeDataUri(admin.getEmail(), secret))
                    .otpAuthUri(totpService.generateOtpAuthUri(admin.getEmail(), secret))
                    .issuer(totpService.getIssuer())
                    .digits(totpService.getDigits())
                    .period(totpService.getPeriod())
                    .build();
        } else {
            long userId = Long.parseLong(principal, 0, principal.length(), 10);
            return setupTotp(userId);
        }
    }

    @Transactional
    public AuthResponse mandatoryEnableTotp(String setupToken, String totpCode,
                                            String ipAddress, String deviceInfo) {
        String principal = resolveSetupTokenPrincipal(setupToken);

        if (principal.startsWith(ADMIN_PRINCIPAL_PREFIX)) {
            long adminId = Long.parseLong(principal, ADMIN_PRINCIPAL_PREFIX.length(), principal.length(), 10);
            Admin admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin", adminId));

            if (admin.getTotpSecret() == null) {
                throw new BusinessException("TOTP not initialized. Call /auth/totp/mandatory-setup first.",
                        "TOTP_NOT_INITIALIZED");
            }
            if (!totpService.verifyCode(admin.getTotpSecret(), totpCode)) {
                throw new InvalidTotpException();
            }

            admin.setTotpEnabled(true);
            adminRepository.save(admin);
            redisTemplate.delete(SETUP_TOKEN_PREFIX + setupToken);

            emailService.send2FAEnabledEmail(admin.getEmail(), admin.getFullName());
            auditService.log("TOTP_ENABLED", "Admin", adminId, admin.getEmail(),
                    "Mandatory 2FA enrollment completed for admin");

            return generateAdminTokenPair(admin, ipAddress, deviceInfo);
        } else {
            long userId = Long.parseLong(principal, 0, principal.length(), 10);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));

            if (user.getTotpSecret() == null) {
                throw new BusinessException("TOTP not initialized. Call /auth/totp/mandatory-setup first.",
                        "TOTP_NOT_INITIALIZED");
            }
            if (!totpService.verifyCode(user.getTotpSecret(), totpCode)) {
                throw new InvalidTotpException();
            }

            user.setTotpEnabled(true);
            userRepository.save(user);

            List<String> rawCodes = generateAndSaveBackupCodes(user);
            redisTemplate.delete(SETUP_TOKEN_PREFIX + setupToken);
            emailService.send2FAEnabledEmail(user.getEmail(), user.getFullName());
            auditService.log("TOTP_ENABLED", "User", userId, user.getEmail(),
                    "Mandatory 2FA enrollment completed");

            AuthResponse response = generateUserTokenPair(user, ipAddress, deviceInfo);
            response.setBackupCodes(rawCodes);
            return response;
        }
    }

    // ─── CHANGE PASSWORD (client) ─────────────────────────────────────
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword, String totpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            boolean validTotp = totpService.verifyCode(user.getTotpSecret(), totpCode);
            if (!validTotp) validTotp = verifyBackupCode(user, totpCode);
            if (!validTotp) throw new InvalidTotpException();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId);
        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFullName());
        auditService.log("PASSWORD_CHANGED", "User", userId, user.getEmail(), "Password changed");
    }

    // ─── REVOKE ALL SESSIONS ──────────────────────────────────────────
    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        auditService.log("ALL_SESSIONS_REVOKED", "User", userId, null, "All sessions revoked by user");
    }

    // ─── FORGOT PASSWORD ──────────────────────────────────────────────
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            user.setPasswordResetToken(UUID.randomUUID().toString());
            user.setPasswordResetExpires(LocalDateTime.now().plusHours(1));
            userRepository.save(user);
            emailService.sendPasswordReset(user.getEmail(), user.getFullName(), user.getPasswordResetToken());
        });
        // Always return OK to prevent email enumeration
    }

    // ─── RESET PASSWORD ───────────────────────────────────────────────
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token", "INVALID_RESET_TOKEN"));

        if (user.getPasswordResetExpires().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Password reset token has expired", "EXPIRED_RESET_TOKEN");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFullName());
        auditService.log("PASSWORD_RESET", "User", user.getId(), user.getEmail(), "Password reset via email");
    }

    // ─── VERIFY EMAIL ─────────────────────────────────────────────────
    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailToken(token)
                .orElseThrow(() -> new BusinessException("Invalid email verification token"));

        if (user.getEmailTokenExpires().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Email verification token has expired");
        }

        user.setEmailVerified(true);
        user.setEmailToken(null);
        user.setEmailTokenExpires(null);
        userRepository.save(user);
        auditService.log("EMAIL_VERIFIED", "User", user.getId(), user.getEmail(), null);
    }

    // ─── Private helpers ──────────────────────────────────────────────

    private AuthResponse generateUserTokenPair(User user, String ipAddress, String deviceInfo) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        String accessToken  = jwtUtils.generateAccessToken(userDetails, user.getId(), roles);
        String refreshValue = jwtUtils.generateRefreshToken(userDetails, user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshValue))
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtils.getRefreshExpirationMs() / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshValue)
                .expiresIn(jwtUtils.getAccessExpirationMs() / 1000)
                .tokenType("Bearer")
                .user(toUserResponse(user))
                .actorType("USER")
                .totpRequired(false)
                .build();
    }

    private AuthResponse generateAdminTokenPair(Admin admin, String ipAddress, String deviceInfo) {
        UserDetails adminDetails = userDetailsService.loadUserByUsername(admin.getEmail());

        List<String> roles = List.of("ROLE_" + admin.getRole().name());

        String accessToken  = jwtUtils.generateAccessToken(adminDetails, admin.getId(), roles);
        String refreshValue = jwtUtils.generateRefreshToken(adminDetails, admin.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .admin(admin)
                .tokenHash(hashToken(refreshValue))
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtils.getRefreshExpirationMs() / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshValue)
                .expiresIn(jwtUtils.getAccessExpirationMs() / 1000)
                .tokenType("Bearer")
                .adminUser(toAdminResponse(admin))
                .actorType(admin.getRole().name())
                .totpRequired(false)
                .build();
    }

    private void handleFailedUserLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        LocalDateTime lockUntil = null;
        if (attempts >= maxLoginAttempts) {
            lockUntil = LocalDateTime.now().plusMinutes(lockoutMinutes);
            log.warn("User account locked for {} minutes: {}", lockoutMinutes, user.getEmail());
        }
        userRepository.incrementFailedAttempts(user.getId(), lockUntil);
    }

    private void handleFailedAdminLogin(Admin admin) {
        int attempts = admin.getFailedLoginAttempts() + 1;
        LocalDateTime lockUntil = null;
        if (attempts >= maxLoginAttempts) {
            lockUntil = LocalDateTime.now().plusMinutes(lockoutMinutes);
            log.warn("Admin account locked for {} minutes: {}", lockoutMinutes, admin.getEmail());
        }
        adminRepository.incrementFailedAttempts(admin.getId(), lockUntil);
    }

    private void incrementTotpAttempts(String attemptsKey) {
        redisTemplate.opsForValue().increment(attemptsKey);
        redisTemplate.expire(attemptsKey, 5, TimeUnit.MINUTES);
    }

    private String resolveSetupTokenPrincipal(String setupToken) {
        String principal = redisTemplate.opsForValue().get(SETUP_TOKEN_PREFIX + setupToken);
        if (principal == null) {
            throw new UnauthorizedException("Invalid or expired setup token. Please login again.");
        }
        return principal;
    }

    private boolean verifyBackupCode(User user, String code) {
        if (code == null || code.length() != 8) return false;
        List<BackupCode> activeCodes = backupCodeRepository.findByUserIdAndUsedFalse(user.getId());
        for (BackupCode bc : activeCodes) {
            if (passwordEncoder.matches(code, bc.getCodeHash())) {
                bc.setUsed(true);
                bc.setUsedAt(LocalDateTime.now());
                backupCodeRepository.save(bc);
                auditService.log("BACKUP_CODE_USED", "User", user.getId(), user.getEmail(),
                        "Backup code used for login");
                return true;
            }
        }
        return false;
    }

    private List<String> generateAndSaveBackupCodes(User user) {
        backupCodeRepository.deleteAllByUserId(user.getId());
        List<BackupCode> codes = new ArrayList<>();
        List<String> rawCodes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String rawCode = generateBackupCode();
            rawCodes.add(rawCode);
            codes.add(BackupCode.builder()
                    .user(user)
                    .codeHash(passwordEncoder.encode(rawCode))
                    .build());
        }
        backupCodeRepository.saveAll(codes);
        return rawCodes;
    }

    private String generateBackupCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(BACKUP_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(BACKUP_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .idCardNumber(user.getIdCardNumber())
                .dateOfBirth(user.getDateOfBirth())
                .status(user.getStatus())
                .totpEnabled(user.getTotpEnabled())
                .emailVerified(user.getEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .build();
    }

    private AdminResponse toAdminResponse(Admin admin) {
        return AdminResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .email(admin.getEmail())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .role(admin.getRole())
                .totpEnabled(admin.getTotpEnabled())
                .active(admin.getActive())
                .lastLoginAt(admin.getLastLoginAt())
                .createdAt(admin.getCreatedAt())
                .build();
    }
}
