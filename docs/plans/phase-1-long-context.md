# Phase 1: Long-Context Baseline

## Goal

Build a working `POST /ask` endpoint that stuffs the entire NCC 2022 Vol 2 PDF into every Claude prompt — no chunking, no retrieval, no caching. Measure token usage, latency, and cost per request so the Phase 2 RAG contrast has real numbers to push against.

## Approach

- **Load the PDF once at startup.** A `@Configuration` bean uses Spring AI's `PagePdfDocumentReader` to extract page text, concatenates it into a single `String`, and exposes a `CorpusText` record with stats (chars, estimated tokens, load duration). The bean is injected into the answer service as a singleton — no re-read per request.
- **One service builds the prompt and calls Claude.** `LongContextAnswerService` formats `<document>{CORPUS}</document>\n\nQuestion: {Q}` into the user message, uses Spring AI's `ChatClient` to call Anthropic, reads token usage off `ChatResponse.getMetadata().getUsage()`, and returns `AnswerResponse` with the answer plus metrics.
- **Thin REST controller, classic Spring layers.** `controller/service/model/config/util` packaging. `POST /ask` takes `{question}` JSON, returns `{answer, inputTokens, outputTokens, latencyMs, estimatedCostUsd, model}`.
- **Model: `claude-haiku-4-5`.** Cheapest 4.5-family model (~$0.12/query vs ~$0.46 on Sonnet). Its weaker long-context recall is a feature for Phase 1 — makes failure modes more visible, strengthens the Phase 2 motivation.
- **Cost table is hardcoded to Haiku pricing only.** YAGNI — add more models when needed.
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

- [ ] Switch `application.properties` model to `claude-haiku-4-5`
- [ ] Add `spring-boot-starter-validation` to `build.gradle`
- [ ] Create `model/CorpusText`, `model/AskRequest`, `model/AnswerResponse` records
- [ ] Create `util/CostCalculator` with Haiku pricing — TDD, pure math
- [ ] Create `config/CorpusLoader` (`@Configuration` + `@Bean CorpusText`) — test with a tiny fixture PDF at `src/test/resources/test-corpus.pdf`
- [ ] Run loader against the real NCC PDF once — **checkpoint:** if estimated tokens ≥ 190K, stop and surface
- [ ] Create `service/LongContextAnswerService` — test with mocked `ChatClient`
- [ ] Create `config/ChatClientConfig` — builds `ChatClient` from injected `ChatClient.Builder`
- [ ] Create `controller/AskController` — `POST /ask`, `@WebMvcTest` with `@MockitoBean`
- [ ] Fix `KnowledgeBotApplicationTests` so the context-load test still passes (inject fake key + fixture PDF path via `@TestPropertySource`)
- [ ] Add `AskEndpointSmokeIT` — env-gated (`@EnabledIfEnvironmentVariable`), hits real Anthropic API, skipped if key or PDF missing
- [ ] `./gradlew build` green
- [ ] Create `Dockerfile` (multi-stage: Gradle build → slim JRE runtime, non-root user) + `.dockerignore`
- [ ] `docker build -t knowledge-bot .` green
- [ ] `docker run -p 8080:8080 --env-file .env -v $(pwd)/corpus:/app/corpus:ro knowledge-bot` — app boots, corpus loads, `/ask` reachable on localhost:8080
- [ ] Run the 6 sample questions via `curl` against the docker container (easy, specific, buried, real clause, fake clause, out-of-corpus); capture JSON responses
- [ ] Write `docs/observations/phase-1-findings.md` with raw Q/A + metrics
- [ ] Fill README `Phase 1 — long-context baseline` section: corpus stats, averages, 2–3 honest observations
