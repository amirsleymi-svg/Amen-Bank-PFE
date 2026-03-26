package com.amenbank.service.impl;

import com.amenbank.dto.request.*;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
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

    private static final String TEMP_TOKEN_PREFIX = "temp_token:";

    // ─── REGISTER ─────────────────────────────────────────────────────
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Check duplicates
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByIdCardNumber(request.getIdCardNumber())) {
            throw new DuplicateResourceException("ID card number already registered");
        }

        // Verify identity against pre-loaded records
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

        // Assign default user role
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("Default role not configured"));
        user.setRoles(Set.of(userRole));

        User saved = userRepository.save(user);

        // Create KYC request
        KycRequest kycRequest = KycRequest.builder()
                .user(saved)
                .build();

        if (identity.isPresent()) {
            // Identity matched — pending admin approval
            kycRequest.setStatus(com.amenbank.enums.KycStatus.PENDING);
        } else {
            // Needs document upload
            kycRequest.setStatus(com.amenbank.enums.KycStatus.PENDING);
        }
        kycRequestRepository.save(kycRequest);

        // Send verification email
        emailService.sendEmailVerification(saved.getEmail(), saved.getFullName(), saved.getEmailToken());

        // Notify admins
        emailService.notifyAdminsNewKycRequest(saved.getFullName(), saved.getEmail());

        auditService.log("USER_REGISTERED", "User", saved.getId(), saved.getEmail(),
                "New user registration - KYC pending");

        log.info("New user registered: {} ({})", saved.getUsername(), saved.getEmail());
        return toUserResponse(saved);
    }

    // ─── LOGIN ────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String deviceInfo) {
        // Load user first to check lock status
        User user = userRepository.findByEmailOrUsername(request.getIdentifier(), request.getIdentifier())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // Check account lock
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account is temporarily locked");
        }

        // Authenticate password
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            handleFailedLogin(user);
            throw e;
        }

        // Reset failed attempts on success
        userRepository.resetLoginAttempts(user.getId());

        // Check account status
        if (!user.isActive()) {
            throw new DisabledException("Account is not active");
        }

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);

        // If TOTP enabled → return temp token for 2FA step
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            String tempToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    TEMP_TOKEN_PREFIX + tempToken,
                    user.getId().toString(),
                    5, TimeUnit.MINUTES
            );
            auditService.log("LOGIN_2FA_PENDING", "User", user.getId(), user.getEmail(), null);
            return AuthResponse.builder()
                    .totpRequired(true)
                    .tempToken(tempToken)
                    .build();
        }

        // Generate tokens
        return generateTokenPair(user, ipAddress, deviceInfo);
    }

    // ─── VERIFY 2FA ───────────────────────────────────────────────────
    @Transactional
    public AuthResponse verifyTotp(TotpVerifyRequest request, String ipAddress, String deviceInfo) {
        String userIdStr = redisTemplate.opsForValue().get(TEMP_TOKEN_PREFIX + request.getTempToken());
        if (userIdStr == null) {
            throw new UnauthorizedException("Invalid or expired session. Please login again.");
        }

        Long userId = Long.parseLong(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Verify TOTP code
        boolean valid = totpService.verifyCode(user.getTotpSecret(), request.getTotpCode());

        if (!valid) {
            // Try backup code
            valid = verifyBackupCode(user, request.getTotpCode());
            if (!valid) {
                throw new InvalidTotpException();
            }
        }

        // Invalidate temp token
        redisTemplate.delete(TEMP_TOKEN_PREFIX + request.getTempToken());

        auditService.log("USER_LOGIN", "User", user.getId(), user.getEmail(),
                "Successful login with 2FA from " + ipAddress);

        return generateTokenPair(user, ipAddress, deviceInfo);
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
            // Token reuse detected — revoke all user tokens
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            throw new UnauthorizedException("Refresh token reuse detected. All sessions invalidated.");
        }

        // Revoke used token (rotation)
        stored.setRevoked(true);
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        return generateTokenPair(user, ipAddress, deviceInfo);
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

    // ─── SETUP TOTP ───────────────────────────────────────────────────
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

        String qrCodeUri = totpService.generateQrCodeDataUri(user.getEmail(), secret);
        String otpAuthUri = totpService.generateOtpAuthUri(user.getEmail(), secret);

        return TotpSetupResponse.builder()
                .secret(secret)
                .qrCodeDataUri(qrCodeUri)
                .otpAuthUri(otpAuthUri)
                .issuer(totpService.getIssuer())
                .digits(totpService.getDigits())
                .period(totpService.getPeriod())
                .build();
    }

    // ─── ENABLE TOTP ──────────────────────────────────────────────────
    @Transactional
    public void enableTotp(Long userId, String totpCode) {
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

        // Generate backup codes
        generateAndSaveBackupCodes(user);

        emailService.send2FAEnabledEmail(user.getEmail(), user.getFullName());
        auditService.log("TOTP_ENABLED", "User", userId, user.getEmail(), "2FA enabled");
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
        // Always return OK to avoid email enumeration
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

        // Revoke all refresh tokens
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
    private AuthResponse generateTokenPair(User user, String ipAddress, String deviceInfo) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        String accessToken = jwtUtils.generateAccessToken(userDetails, user.getId(), roles);
        String refreshTokenValue = jwtUtils.generateRefreshToken(userDetails, user.getId());

        // Store hashed refresh token
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshTokenValue))
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtils.getRefreshExpirationMs() / 1000))
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .expiresIn(jwtUtils.getAccessExpirationMs() / 1000)
                .tokenType("Bearer")
                .user(toUserResponse(user))
                .totpRequired(false)
                .build();
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        LocalDateTime lockUntil = null;
        if (attempts >= maxLoginAttempts) {
            lockUntil = LocalDateTime.now().plusMinutes(lockoutMinutes);
            log.warn("Account locked for {} minutes: {}", lockoutMinutes, user.getEmail());
        }
        userRepository.incrementFailedAttempts(user.getId(), lockUntil);
    }

    private boolean verifyBackupCode(User user, String code) {
        if (code == null || code.length() != 8) return false;
        String codeHash = passwordEncoder.encode(code);
        Optional<BackupCode> backupCode = backupCodeRepository
                .findByUserIdAndCodeHashAndUsedFalse(user.getId(), codeHash);

        // BCrypt doesn't support findByHash; we need to iterate
        List<BackupCode> activeCodes = backupCodeRepository.findByUserIdAndUsedFalse(user.getId());
        for (BackupCode bc : activeCodes) {
            if (passwordEncoder.matches(code, bc.getCodeHash())) {
                bc.setUsed(true);
                bc.setUsedAt(LocalDateTime.now());
                backupCodeRepository.save(bc);
                auditService.log("BACKUP_CODE_USED", "User", user.getId(), user.getEmail(), "Backup code used for login");
                return true;
            }
        }
        return false;
    }

    private void generateAndSaveBackupCodes(User user) {
        backupCodeRepository.deleteAllByUserId(user.getId());
        List<BackupCode> codes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String rawCode = generateBackupCode();
            codes.add(BackupCode.builder()
                    .user(user)
                    .codeHash(passwordEncoder.encode(rawCode))
                    .build());
        }
        backupCodeRepository.saveAll(codes);
    }

    private String generateBackupCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
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
}
