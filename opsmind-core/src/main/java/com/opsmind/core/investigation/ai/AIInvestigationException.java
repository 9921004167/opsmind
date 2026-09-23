package com.opsmind.core.investigation.ai;

/** Covers both "provider unreachable/misconfigured" and "response failed
 *  validation" - InvestigationService treats both the same way: mark the
 *  investigation FAILED with the message here, never persist a partial/invalid
 *  RcaFinding, never crash the incident. */
public class AIInvestigationException extends RuntimeException {
    public AIInvestigationException(String message) {
        super(message);
    }
    public AIInvestigationException(String message, Throwable cause) {
        super(message, cause);
    }
}
