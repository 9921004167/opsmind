package com.acme.incident.memory.embedding;

/**
 * gemini-embedding-2 accepts a free-text task instruction that shapes the
 * vector for the relationship you actually care about.
 *
 * Retrieval here is asymmetric: the stored side is a full post-mortem, the
 * query side is a live incident with symptoms but no root cause yet. Using
 * one instruction for both biases top-K toward documents that merely look
 * like incident reports.
 */
public enum EmbeddingTask {

    /** Used when writing a resolved incident into memory. */
    DOCUMENT("task: index a resolved production incident report so it can be "
            + "retrieved later by the symptoms it presented"),

    /** Used when searching with an active, unresolved incident. */
    QUERY("task: find past resolved incidents whose symptoms and affected "
            + "services resemble this active incident");

    private final String instruction;

    EmbeddingTask(String instruction) {
        this.instruction = instruction;
    }

    public String instruction() {
        return instruction;
    }
}
