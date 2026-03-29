package com.amenbank.service;

import com.amenbank.dto.request.ActivateAccountRequest;
import com.amenbank.dto.request.CreateAccountFromRequestDto;
import com.amenbank.dto.request.RegistrationRequestDto;
import com.amenbank.dto.response.RegistrationRequestResponse;
import com.amenbank.dto.response.PageResponse;
import com.amenbank.dto.response.UserResponse;
import com.amenbank.enums.RegistrationStatus;

public interface OnboardingService {

    /**
     * Client submits email + confirmEmail → creates a PENDING registration request.
     */
    RegistrationRequestResponse submitRegistrationRequest(RegistrationRequestDto dto, String ipAddress);

    /**
     * Admin lists all registration requests (optionally filtered by status).
     */
    PageResponse<RegistrationRequestResponse> listRegistrationRequests(int page, int size, RegistrationStatus status);

    /**
     * Admin creates a user account (role=CLIENT, disabled) from an approved request,
     * generates activation token, sends email.
     */
    UserResponse createAccountFromRequest(CreateAccountFromRequestDto dto, String adminEmail);

    /**
     * Admin rejects a registration request.
     */
    void rejectRegistrationRequest(Long requestId, String reason, String adminEmail);

    /**
     * Client activates account: validates token, sets BCrypt password, enables account.
     */
    void activateAccount(ActivateAccountRequest request);
}
