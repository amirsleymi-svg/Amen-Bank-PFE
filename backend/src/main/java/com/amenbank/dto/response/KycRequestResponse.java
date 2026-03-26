package com.amenbank.dto.response;
import com.amenbank.enums.KycStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder
public class KycRequestResponse {
    private Long id;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private KycStatus status;
    private String rejectionReason;
    private int documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
