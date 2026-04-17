# Knowledge Bot

A RAG-based knowledge assistant built as a learning/portfolio project. Corpus: **Australian National Construction Code (NCC) 2022 Volume 2** — residential buildings.

The goal is not a product. The goal is to understand RAG tradeoffs deeply enough to write a credible README about them. The README *is* the deliverable.

## Why this corpus

NCC 2022 Vol 2 is:
- **Claude-weak** — Australian regulatory text with numeric clause references (`H3.2(1)(b)`). LLMs routinely hallucinate clause numbers on this content.
- **Structured** — numbered parts → sections → clauses, each self-contained and citable.
- **Right-sized** — 312 pages / ~607K tokens. Too large for long-context (3.2× Claude's 200K window), which makes Phase 2 RAG necessary rather than optional.
- **High-stakes** — wrong building-code advice has physical consequences, so "fail closed" honesty isn't abstract.

## Stack

- Java 21 + Spring Boot 4.0
- Spring AI (currently 2.0.0-M4, see `build.gradle`)
- Anthropic Claude via `spring-ai-starter-model-anthropic`
- Postgres + pgvector (Phase 2+)

## Setup

```bash
# 1. Get the corpus
#    → https://ncc.abcb.gov.au, register free, download NCC 2022 Vol 2 PDF
#    → save as corpus/ncc-2022-vol2.pdf

# 2. Set your API key
cp .env.example .env
# edit .env, paste your key from console.anthropic.com

# 3. Run
./run.sh
```

## Phases

| Phase | Status | What |
|---|---|---|
| 1 — Long-context baseline | **done** | measured corpus, proved long-context impossible for full NCC — see [findings](docs/observations/phase-1-findings.md) |
| 2 — Basic RAG | — | pgvector + ingestion + `QuestionAnswerAdvisor` |
| 3 — Honesty layer | — | strict refusal, structured output with `can_answer`, relative confidence thresholds |
| 4 — Evaluation harness | — | 30–50 golden Q/A (~20% unanswerable), false-confidence rate as primary metric |
| 5 — Depth pick | — | one of: hybrid retrieval, streaming citations, multi-doc, prompt caching |

## Results

_To be filled after each phase._

### Phase 1 — long-context baseline

The plan was to stuff the entire NCC PDF into every Claude prompt as a baseline. Measurement killed that plan:

| Metric | Value |
|---|---|
| Corpus | NCC 2022 Vol 2 (Housing Provisions) |
| Pages | 312 |
| Estimated tokens | ~607K |
| Claude context window | ~200K |
| **Ratio** | **3.2×** — doesn't fit |

The 190K checkpoint in `CorpusLoader` refused to boot — as designed. No silent truncation, no model swap. The long-context approach fails by construction for a real-world corpus this size.

This is the Phase 2 motivation: chunking + retrieval makes the full corpus queryable within the context budget. See [full findings](docs/observations/phase-1-findings.md).

### Phase 2 — RAG comparison
_pending_

### Phase 4 — eval metrics
_pending_

## Design decisions

Decisions made during development and what a production version would do differently. These capture the tradeoff reasoning — not just what I built, but what I chose *not* to build and why.

### Ingestion: startup check vs separate pipeline

A RAG system has two pipelines: **ingestion** (PDF → chunks → embeddings → vector store) and **query** (/ask → retrieve → augment → generate). The question is whether they belong in the same process.

**What I built:** the app checks for an empty vector store at startup and ingests if needed. Simple, single-process, no operational overhead.

**The problem this ignores:** if the corpus changes, re-ingestion while the app is serving queries creates read-write contention. Users could get partial results, a mix of old and new chunks, or empty results mid-wipe.

**What a production system would do:** blue-green versioning for the vector store.
1. Each chunk carries a `version` column. The app queries `WHERE version = :active`.
2. Re-ingestion writes new chunks as version N+1 (append, no deletes).
3. A `corpus_version` row in the database tracks the active version. Once ingestion completes, the script flips this pointer.
4. The app reads the active version per query (or caches with short TTL) — zero downtime, zero restarts.
5. Old version rows are cleaned up at leisure.

**Why I didn't build it:** one PDF, one developer, no users. The corpus won't change mid-session. Building the versioning infrastructure would be YAGNI — but understanding *why* it exists and being able to describe it matters more than the implementation.

## What I learned

_Left blank on purpose. This fills in at the end — the surprises, the wrong turns, and where this architecture would break down at the next order of magnitude._
