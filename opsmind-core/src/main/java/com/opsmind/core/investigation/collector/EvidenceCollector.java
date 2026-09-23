package com.opsmind.core.investigation.collector;

import com.opsmind.core.investigation.Evidence;

import java.util.List;

/**
 * Implementations must NEVER throw for "source unreachable" or "no data found" -
 * both are legitimate real-world outcomes and should result in an empty list (or
 * partial results), logged, so one collector's failure never blocks the others or
 * fails the whole investigation. Only implementations that fabricate data would be
 * a spec violation - returning nothing is always safe.
 */
public interface EvidenceCollector {
    List<Evidence> collect(EvidenceCollectionContext context);

    /** Used in logs/audit to say which collectors ran. */
    String name();
}
