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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, digits);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

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
        return "otpauth://totp/" + issuer + ":" + accountName
                + "?secret=" + secret
                + "&issuer=" + issuer
                + "&algorithm=SHA1"
                + "&digits=" + digits
                + "&period=" + period;
    }

    public int getDigits() { return digits; }
    public int getPeriod() { return period; }
    public String getIssuer() { return issuer; }
}
