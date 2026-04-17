# Phase 1: Long-Context Baseline

## Goal

Build a working `POST /ask` endpoint that stuffs the Phase 1 corpus subset into every Claude prompt — no chunking, no retrieval, no caching. Measure token usage, latency, and cost per request so the Phase 2 RAG contrast has real numbers to push against.

**Scope decision (2026-04-16, after measuring):** the full NCC 2022 Vol 2 is 312 pages / ~607K tokens — 3.2× Claude's 200K window. The 190K checkpoint fired as designed. Phase 1 baseline is bounded to **Part H4 — Health and amenity (pages 117–125, ~9 pages, ~18K tokens, 72K chars)** — a self-contained residential section covering wet areas, room heights, ventilation, lighting, and sound insulation. Phase 2 RAG targets the full Vol 2.

## Approach

- **Load the PDF once at startup.** A `@Configuration` bean (`CorpusLoader`) uses Spring AI's `PagePdfDocumentReader` to extract page text, slices to the configured `page-from`/`page-to` range, concatenates into a single `String`, and exposes a `CorpusText(String text)` bean. Stats (chars, estimated tokens, load duration) logged at startup. Bean is injected into the answer service as a singleton — no re-read per request.
- **Page-range scoping is a first-class config knob.** `knowledgebot.corpus.page-from` / `.page-to` (1-based, inclusive) let us bound a large PDF without preprocessing. Phase 1 scopes to NCC pages 117–125 (Part H4); Phase 2 will drop the range to read the full Vol 2 for indexing.
- **One service builds the prompt and calls Claude.** `LongContextAnswerService` formats `<document>{CORPUS}</document>\n\nQuestion: {Q}` into the user message, uses Spring AI's `ChatClient` to call Anthropic, reads token usage off `ChatResponse.getMetadata().getUsage()`, and returns `AnswerResponse` with the answer plus metrics.
- **Thin REST controller, classic Spring layers.** `controller/service/model/config/util` packaging. `POST /ask` takes `{question}` JSON, returns `{answer, metrics: {inputTokens, outputTokens, latencyMs, estimatedCostUsd}}` — answer is the API contract, metrics is sidecar observability that frontends can ignore.
- **Model: `claude-haiku-4-5`.** Cheapest 4.5-family model (~$0.12/query vs ~$0.46 on Sonnet). Its weaker long-context recall is a feature for Phase 1 — makes failure modes more visible, strengthens the Phase 2 motivation.
- **Pricing lives in a `ModelPricing` enum.** Phase 1 has one entry (Haiku 4.5); adding a model later is a one-line enum addition. The String→enum lookup (`ModelPricing.forModel(configuredId)`) runs once at startup and throws loudly on unknown ids — so a config drift between `application.properties` and the price table fails the boot, not silently miscalculates cost. `CostCalculator.estimate(ModelPricing, in, out)` then takes the enum directly — type-safe, no String typos at the math site.
- **Measurement surfaces in the response itself.** Metrics embedded in every `POST /ask` response so `curl` iteration is self-documenting. Also logged at INFO.
- **No honesty layer yet.** System prompt says "use only the provided text", but there's no structured refusal, no `can_answer` flag, no confidence threshold. Phase 3. We *want* Phase 1 to hallucinate on fake-clause questions so Phase 3 has something to fix.
- **Decision checkpoint after PDF extraction.** If estimated tokens ≥ 190K (Claude's ~200K context minus headroom), stop and surface to Harry — don't silently truncate or swap models.
- **Local run via Docker.** Multi-stage `Dockerfile` (Gradle build stage → JRE runtime stage) produces a slim image. Corpus PDF mounted as a read-only volume at runtime — keeps 6.5MB of copyrighted content out of the image and out of git. API key injected via `--env-file .env`. Port 8080 exposed. `docker-compose.yml` deferred to Phase 2 when Postgres/pgvector joins.

### Known gotchas

- Spring AI 2.0.0-M4: response text is `.call().content()` (not `.getText()`); `Document.getText()` (not `getContent()`); `@MockitoBean` replaces deprecated `@MockBean`.
- `ChatClient` is a fluent interface — unit test mocks use deep stubs or `RETURNS_SELF` on the request spec.
- `application.properties` currently has `claude-sonnet-4-5` from scaffolding — the first TODO below switches it to Haiku.

### Out of scope (Phase 2+)

Chunking, embeddings, vector store, retrieval (Phase 2). Prompt caching (Phase 5). Structured refusal / `can_answer` / confidence (Phase 3). Golden dataset and automated eval (Phase 4). Streaming. Multi-document.

## TODO

- [x] Switch `application.properties` model to `claude-haiku-4-5`
- [x] Add `spring-boot-starter-validation` to `build.gradle`
- [x] Create `model/CorpusText`, `model/AskRequest`, `model/AnswerResponse`, `model/AnswerMetrics` records
- [x] Create `util/ModelPricing` enum (Haiku entry) + `util/CostCalculator` with model-keyed lookup — TDD, pure math
- [x] Create `config/CorpusLoader` (`@Configuration` + `@Bean CorpusText`, page-range support) — tested with committed fixture PDF at `src/test/resources/test-corpus.pdf` (generator helper: `TestPdfFixtureGenerator`, `@Disabled`)
- [x] Run loader against the real NCC PDF once — **checkpoint fired**: full PDF = 607K tokens (3.2× window). Pivoted to Part H4 (pages 117–125, ~18K tokens). Verified via `VerifyNccPart4Range` helper.
- [x] Fix `KnowledgeBotApplicationTests` so the context-load test still passes (fake key + fixture PDF path via `@TestPropertySource`)
- [x] Write `docs/observations/phase-1-findings.md`
- [x] Fill README `Phase 1 — long-context baseline` section

### Skipped (Phase 1 concluded early)

The checkpoint measurement proved the long-context approach impossible for the full corpus. Building a `/ask` endpoint against an 18K-token subset wouldn't produce representative baseline numbers — the real finding is the hard wall, not latency stats on a trivially small context. Remaining infrastructure (service, controller, Docker, smoke IT, curl tests) deferred to Phase 2 where they'll be wired to RAG instead.

- ~Create `service/LongContextAnswerService`~
- ~Create `config/ChatClientConfig`~
- ~Create `controller/AskController`~
- ~Add `AskEndpointSmokeIT`~
- ~`./gradlew build` green~
- ~Create `Dockerfile` + `.dockerignore`~
- ~`docker build` / `docker run`~
- ~Run 6 sample questions via `curl`~
