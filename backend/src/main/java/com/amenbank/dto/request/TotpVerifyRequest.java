package com.amenbank.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TotpVerifyRequest {
    @NotBlank(message = "Temporary token is required")
    private String tempToken;

    @NotBlank(message = "TOTP code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "TOTP code must be exactly 6 digits")
    private String totpCode;
}
