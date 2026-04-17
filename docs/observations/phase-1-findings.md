# Phase 1 Findings: Long-Context Baseline

## Corpus measurement

| Metric | Value |
|---|---|
| Document | NCC 2022 Volume Two (Housing Provisions) |
| Pages | 312 |
| Characters | 2,427,096 |
| Estimated tokens (chars ÷ 4) | ~607K |
| Load time (full PDF, PagePdfDocumentReader) | 1.7s |

## Finding: long-context approach is impossible for this corpus

Claude's context window is ~200K tokens. Leaving 10K headroom for the system prompt, question, and answer, the corpus ceiling is ~190K tokens.

The full NCC Vol 2 estimates at **607K tokens — 3.2× the ceiling**. There is no way to fit this corpus into a single Claude prompt. The chars/4 heuristic could be off by ±20%, but no estimation error closes a 3× gap.

The 190K checkpoint in `CorpusLoader` fired exactly as designed: the app refused to boot and surfaced the measurement rather than silently truncating or swapping models.

## Why not use a subset?

We verified that Part H4 (Health and amenity, pages 117–125) extracts cleanly at **~18K tokens** — well within budget. But running a `/ask` endpoint against 18K tokens doesn't produce a meaningful long-context baseline:

- 18K tokens is trivially small — any LLM handles it with near-perfect recall.
- The baseline was supposed to measure the *pain* of long context (high token cost, latency, degraded recall). With only 18K tokens, there's no pain to measure.
- Phase 2's cost/latency improvement over Phase 1 would be marginal, undermining the RAG motivation story.

The real Phase 1 finding isn't a set of latency numbers — it's that the approach **fails by construction** for a real-world corpus of this size.

## What this means for Phase 2

The long-context approach's failure is itself the clearest possible motivation for RAG:

1. **Chunking + embeddings** break the corpus into pieces small enough to fit any context window.
2. **Retrieval** selects only the relevant chunks per question — token usage drops from "entire corpus" to "a few relevant passages."
3. **The /ask endpoint** (service, controller, Docker, smoke tests) gets built in Phase 2, wired to RAG from the start rather than long context.

Infrastructure that Phase 1 built and Phase 2 inherits:
- `CorpusLoader` (page-range support, checkpoint enforcement)
- `CorpusText` record (slim wrapper)
- `ModelPricing` enum + `CostCalculator` (type-safe, model-keyed)
- `AskRequest` / `AnswerResponse` / `AnswerMetrics` model records
- `application.properties` config structure
- Test fixtures and helpers
