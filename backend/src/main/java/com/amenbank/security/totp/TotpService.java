package com.amenbank.security.totp;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.util.Utils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class TotpService {

    @Value("${totp.issuer:AmenBank}")
    private String issuer;

    @Value("${totp.period:30}")
    private int period;

    @Value("${totp.digits:6}")
    private int digits;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private CodeGenerator codeGenerator;
    private CodeVerifier codeVerifier;

    @PostConstruct
    void init() {
        if (digits <= 0) {
            log.warn("Invalid totp.digits value ({}), defaulting to 6", digits);
            digits = 6;
        }
        if (period <= 0) {
            log.warn("Invalid totp.period value ({}), defaulting to 30", period);
            period = 30;
        }
        codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, digits);
        codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    }

    /**
     * Generate a new TOTP secret for a user.
     */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Verify a TOTP code against a secret.
     * Accepts current and adjacent windows (±1) for clock drift.
     */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || code.length() != digits) {
            return false;
        }
        try {
            return ((DefaultCodeVerifier) codeVerifier).isValidCode(secret, code);
        } catch (Exception e) {
            log.warn("TOTP verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate a QR code data URI (base64 PNG) for Google Authenticator enrollment.
     */
    public String generateQrCodeDataUri(String accountName, String secret) throws QrGenerationException {
        QrData qrData = new QrData.Builder()
                .label(accountName)
                .secret(secret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(digits)
                .period(period)
                .build();

        QrGenerator generator = new ZxingPngQrGenerator();
        byte[] imageData = generator.generate(qrData);
        String mimeType = generator.getImageMimeType();

        return Utils.getDataUriForImage(imageData, mimeType);
    }

    /**
     * Generate the otpauth:// URI (for manual entry in authenticator apps).
     */
    public String generateOtpAuthUri(String accountName, String secret) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encodedAccountName = URLEncoder.encode(accountName, StandardCharsets.UTF_8);
        return "otpauth://totp/" + encodedIssuer + ":" + encodedAccountName
                + "?secret=" + secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1"
                + "&digits=" + digits
                + "&period=" + period;
    }

    public int getDigits() { return digits; }
    public int getPeriod() { return period; }
    public String getIssuer() { return issuer; }
}
