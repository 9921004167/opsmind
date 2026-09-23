package com.opsmind.core.investigation.dto;

import com.opsmind.core.investigation.EvidenceType;

import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        EvidenceType type,
        String source,
        Instant observedAt,
        String title,
        String description,
        String observedValue
) {}
