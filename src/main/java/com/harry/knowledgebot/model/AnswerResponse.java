package com.harry.knowledgebot.model;

/**
 * Response body for POST /ask. The answer is the API contract;
 * `metrics` is sidecar observability data (token usage, latency, cost)
 * that frontends can ignore but Phase 4's eval harness will consume.
 */
public record AnswerResponse(
        String answer,
        AnswerMetrics metrics
) {
}
