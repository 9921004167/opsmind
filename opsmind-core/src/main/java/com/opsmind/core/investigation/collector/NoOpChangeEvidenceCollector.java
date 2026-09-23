package com.opsmind.core.investigation.collector;

import com.opsmind.core.investigation.Evidence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HONEST LIMITATION: no deployment/change-tracking system exists yet (no CI/CD
 * event feed, no Deployment entity). This returns nothing rather than fabricate
 * "deployment at 10:00" events. When real deployment metadata exists, replace this
 * bean the same way as NoOpLogEvidenceCollector.
 */
@Component
@Slf4j
public class NoOpChangeEvidenceCollector implements ChangeEvidenceCollector {

    @Override
    public String name() {
        return "deployments/changes (unavailable)";
    }

    @Override
    public List<Evidence> collect(EvidenceCollectionContext context) {
        log.info("Deployment/change evidence collection skipped for investigation {} - no deployment tracking system is configured yet.",
                context.investigationId());
        return List.of();
    }
}
