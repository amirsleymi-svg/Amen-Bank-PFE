package com.amenbank.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TotpVerifyRequest {
    @NotBlank(message = "Temporary token is required")
    private String tempToken;

    @NotBlank(message = "TOTP code is required")
    @Pattern(
        regexp = "^([0-9]{6}|[A-Z2-9]{8})$",
        message = "Must be a 6-digit TOTP code or an 8-character backup code"
    )
    private String totpCode;
}
