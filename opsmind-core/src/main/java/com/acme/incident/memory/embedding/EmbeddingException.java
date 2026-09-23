package com.acme.incident.memory.embedding;

public class EmbeddingException extends RuntimeException {

    private final boolean retryable;

    public EmbeddingException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
