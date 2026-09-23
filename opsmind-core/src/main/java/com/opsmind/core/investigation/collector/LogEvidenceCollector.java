package com.opsmind.core.investigation.collector;

/** Separate interface (rather than folding into EvidenceCollector directly) so a
 *  future real implementation (e.g. backed by Loki) is a drop-in replacement bean -
 *  no orchestration code changes required. */
public interface LogEvidenceCollector extends EvidenceCollector {
}
