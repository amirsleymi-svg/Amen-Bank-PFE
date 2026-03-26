package com.amenbank.dto.response;
import lombok.*;

@Data @Builder
public class TotpSetupResponse {
    private String secret;
    private String qrCodeDataUri;
    private String otpAuthUri;
    private String issuer;
    private int digits;
    private int period;
}
