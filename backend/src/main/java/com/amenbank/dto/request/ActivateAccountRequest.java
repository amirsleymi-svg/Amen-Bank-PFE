package com.amenbank.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Client activates their account by submitting the token received by email.
 * The account password was already set during the registration request.
 */
@Data
public class ActivateAccountRequest {

    @NotBlank(message = "Activation token is required")
    private String token;
}
