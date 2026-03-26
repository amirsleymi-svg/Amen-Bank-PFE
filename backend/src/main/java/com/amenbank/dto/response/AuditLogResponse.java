package com.amenbank.dto.response;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder
public class AuditLogResponse {
    private Long id;
    private String actorType;
    private String actorEmail;
    private String action;
    private String entityType;
    private Long entityId;
    private String description;
    private String ipAddress;
    private String status;
    private LocalDateTime createdAt;
}
