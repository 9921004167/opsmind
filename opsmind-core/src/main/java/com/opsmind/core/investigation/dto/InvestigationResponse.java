package com.opsmind.core.investigation.dto;

import com.opsmind.core.investigation.InvestigationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvestigationResponse(
        UUID id,
        UUID incidentId,
        InvestigationStatus status,
        String failureReason,
        String aiProvider,
        String aiModel,
        Instant startedAt,
        Instant completedAt,
        List<EvidenceResponse> evidence,
        RcaFindingResponse rcaFinding // null if not yet COMPLETED
) {}
