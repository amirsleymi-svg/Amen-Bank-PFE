package com.amenbank.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Client submits an onboarding request with only email + confirmEmail.
 * No password, no ID card needed at this stage.
 */
@Data
public class RegistrationRequestDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Please confirm your email")
    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String confirmEmail;
}
