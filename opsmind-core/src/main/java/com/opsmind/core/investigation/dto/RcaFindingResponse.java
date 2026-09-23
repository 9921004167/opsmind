package com.opsmind.core.investigation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RcaFindingResponse(
        UUID id,
        String rootCause,
        String hypothesis,
        String confidenceLevel,
        Double confidenceScore,
        String affectedService,
        String impact,
        List<UUID> supportingEvidenceIds,
        String reasoningSummary,
        List<String> recommendedActions,
        String modelProvider,
        String modelName,
        Instant generatedAt
) {}
