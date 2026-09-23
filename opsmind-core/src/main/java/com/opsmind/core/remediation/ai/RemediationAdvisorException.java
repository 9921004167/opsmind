package com.opsmind.core.remediation.ai;

public class RemediationAdvisorException extends RuntimeException {
    public RemediationAdvisorException(String message) {
        super(message);
    }
    public RemediationAdvisorException(String message, Throwable cause) {
        super(message, cause);
    }
}
