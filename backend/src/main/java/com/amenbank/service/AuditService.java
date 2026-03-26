package com.amenbank.service;

import com.amenbank.enums.AuditActorType;

public interface AuditService {

    void log(String action, String entityType, Long entityId, String actorEmail, String description);

    void log(String action, String entityType, Long entityId, String actorEmail,
             String description, AuditActorType actorType, Long actorId,
             String ipAddress, String userAgent, String requestId);
}
