package com.opsmind.core.common;

/** Base type for exceptions representing a violation of a domain rule (e.g. an illegal state transition). */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
