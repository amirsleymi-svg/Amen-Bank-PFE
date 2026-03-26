package com.amenbank.service.impl;

import com.amenbank.entity.AuditLog;
import com.amenbank.enums.AuditActorType;
import com.amenbank.repository.AuditLogRepository;
import com.amenbank.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Async
    public void log(String action, String entityType, Long entityId,
                    String actorEmail, String description) {
        String ipAddress = null;
        String userAgent = null;
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                ipAddress = attrs.getRequest().getRemoteAddr();
                userAgent = attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception ignored) {}

        AuditLog audit = AuditLog.builder()
                .actorType(AuditActorType.USER)
                .actorEmail(actorEmail)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestId(UUID.randomUUID().toString())
                .status("SUCCESS")
                .build();

        auditLogRepository.save(audit);
        log.debug("AUDIT [{}] entity={}/{} actor={}", action, entityType, entityId, actorEmail);
    }

    @Override
    @Async
    public void log(String action, String entityType, Long entityId, String actorEmail,
                    String description, AuditActorType actorType, Long actorId,
                    String ipAddress, String userAgent, String requestId) {
        AuditLog audit = AuditLog.builder()
                .actorType(actorType)
                .actorId(actorId)
                .actorEmail(actorEmail)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestId(requestId != null ? requestId : UUID.randomUUID().toString())
                .status("SUCCESS")
                .build();

        auditLogRepository.save(audit);
    }
}
