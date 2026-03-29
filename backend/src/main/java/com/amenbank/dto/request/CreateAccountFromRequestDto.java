package com.amenbank.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Admin creates a user account from a registration request.
 * Includes basic profile info; password is set by client via activation token.
 */
@Data
public class CreateAccountFromRequestDto {

    @NotNull(message = "Registration request ID is required")
    private Long registrationRequestId;

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @Size(max = 20)
    private String phoneNumber;

    private LocalDate dateOfBirth;
    private String address;
}
