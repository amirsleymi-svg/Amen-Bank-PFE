package com.amenbank.dto.response;

import com.amenbank.enums.RegistrationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RegistrationRequestResponse {
    private Long id;
    private String email;
    private RegistrationStatus status;
    private String rejectionReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String ipAddress;
    private LocalDateTime createdAt;
}
