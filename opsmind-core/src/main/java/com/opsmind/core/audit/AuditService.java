package com.opsmind.core.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void record(UUID organizationId, UUID actorUserId, String action, String entityType, String entityId, String metadataJson) {
        AuditLog log = AuditLog.builder()
                .organizationId(organizationId)
                .actorUserId(actorUserId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadataJson)
                .build();
        auditLogRepository.save(log);
    }

    /** Used by Kafka consumers, which act as "system", not a specific human user. */
    public void recordSystemAction(UUID organizationId, String actorLabel, String action, String entityType, String entityId, String metadataJson) {
        AuditLog log = AuditLog.builder()
                .organizationId(organizationId)
                .actorLabel(actorLabel)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadataJson)
                .build();
        auditLogRepository.save(log);
    }
}
