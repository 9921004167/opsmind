package com.opsmind.core.investigation.collector;

import com.opsmind.core.investigation.Evidence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HONEST LIMITATION, NOT A BUG: this codebase has no centralized log aggregation
 * system (e.g. Loki) wired up as of Phase 6. Rather than fabricate log evidence or
 * silently skip logging the gap, this collector explicitly returns nothing and logs
 * why, every time it runs. Replace this bean with a real implementation (e.g.
 * LokiLogEvidenceCollector) once a log backend exists - InvestigationService needs
 * no changes to pick it up, since it just autowires all EvidenceCollector beans.
 */
@Component
@Slf4j
public class NoOpLogEvidenceCollector implements LogEvidenceCollector {

    @Override
    public String name() {
        return "logs (unavailable)";
    }

    @Override
    public List<Evidence> collect(EvidenceCollectionContext context) {
        log.info("Log evidence collection skipped for investigation {} - no centralized log store is configured yet.",
                context.investigationId());
        return List.of();
    }
}
