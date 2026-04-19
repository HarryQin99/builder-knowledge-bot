# Phase 2: Basic RAG

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working RAG pipeline that makes the full 312-page NCC PDF queryable via `POST /ask` — ingest into pgvector at startup if empty, retrieve relevant chunks per question via `QuestionAnswerAdvisor`, return answers with token/cost metrics. Dockerised with docker-compose.

**Architecture:** Two pipelines sharing a vector store. Ingestion (one-time at startup): PDF → chunks → embeddings → pgvector. Query (per request): question → vector search → augment prompt with retrieved chunks → Claude → response. Spring AI's `QuestionAnswerAdvisor` handles retrieval + augmentation transparently.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Spring AI 2.0.0-M4, Anthropic Claude (chat via `claude-haiku-4-5`), OpenAI `text-embedding-3-small` (embeddings), Postgres + pgvector, Docker Compose

---

## Approach

- **OpenAI `text-embedding-3-small` for embeddings.** Anthropic doesn't offer an embedding API — need a second provider. OpenAI is cheapest (~$0.02/M tokens), 1536 dimensions. Requires `OPENAI_API_KEY` in `.env`. Spring AI auto-configures `EmbeddingModel` via `spring-ai-starter-model-openai`.
- **pgvector for vector storage.** `spring-ai-starter-vector-store-pgvector` auto-configures `PgVectorStore`. Schema auto-created at startup (`initialize-schema: true`). HNSW index, cosine distance, dimensions=1536 to match the embedding model.
- **Docker Compose for local dev.** Two services: `postgres` (pgvector/pgvector:pg16) + `app` (multi-stage Dockerfile). Corpus PDF mounted as read-only volume into app. `.env` feeds both API keys. `docker-compose up --build` is the single command to run everything.
- **Ingestion runs at startup if vector store is empty.** `IngestionService` checks for existing data on `ApplicationReadyEvent`. If empty: reads the full NCC PDF via `PagePdfDocumentReader`, chunks with `TokenTextSplitter` (default ~800 tokens, no overlap in Spring AI 2.0.0-M4), stores via `VectorStore.add()` (embeddings computed automatically). No versioning, no re-ingestion script — YAGNI. Production approach documented in README's Design Decisions section.
- **`QuestionAnswerAdvisor` wires retrieval + augmentation.** Configured on the `ChatClient` as a default advisor. Per question: embeds the question, searches pgvector for top-K similar chunks, injects them into the prompt template, calls Claude. Developer writes zero retrieval code.
- **Remove Phase 1 long-context artifacts.** `CorpusLoader` and `CorpusText` were for prompt-stuffing — irrelevant for RAG. Replaced by `IngestionService`. Git history preserves Phase 1 code.
- **Reuse Phase 1 model records.** `AskRequest`, `AnswerResponse`, `AnswerMetrics`, `ModelPricing`, `CostCalculator` carry over unchanged — the response envelope is the same regardless of context source.
- **OpenAI ChatModel auto-config conflict.** `spring-ai-starter-model-openai` auto-configures both `ChatModel` and `EmbeddingModel`. We only want embeddings from OpenAI (Claude handles chat). Need to exclude the OpenAI `ChatModel` auto-config or use property-based model selection to avoid bean conflicts.

### Known gotchas

- `QuestionAnswerAdvisor` uses builder pattern: `QuestionAnswerAdvisor.builder(vectorStore).searchRequest(SearchRequest.builder().topK(4).build()).build()`.
- `PgVectorStore` needs `initialize-schema: true` explicitly (default false) — otherwise ingestion fails with missing table.
- `TokenTextSplitter` does NOT support chunk overlap in Spring AI 2.0.0-M4. Uses punctuation-aware sentence boundary splitting instead.
- `text-embedding-3-small` dimensions (1536) must match `spring.ai.vectorstore.pgvector.dimensions`.
- `Document.getText()` not `getContent()` in Spring AI 2.0.0-M4.
- `@MockitoBean` replaces deprecated `@MockBean` (Spring Boot 4).
- `ChatClient` fluent API: `.call().chatResponse()` for full response with metadata; `.call().content()` for just the text.

### Out of scope (Phase 3+)

Structured refusal / `can_answer` / confidence (Phase 3). Golden dataset and automated eval (Phase 4). Structure-aware chunking, hybrid retrieval, streaming, prompt caching (Phase 5).

## TODO

- [ ] Add `spring-ai-starter-model-openai` + `spring-ai-starter-vector-store-pgvector` to `build.gradle`
- [ ] Create `Dockerfile` (multi-stage: Gradle build → JRE runtime, non-root user) + `.dockerignore`
- [ ] Create `docker-compose.yml` — `postgres` (pgvector:pg16) + `app` services, corpus volume, `.env` for keys
- [ ] Update `application.properties` — OpenAI embedding config, datasource, pgvector (dimensions=1536, initialize-schema=true, HNSW), remove page-from/page-to
- [ ] Update `.env.example` with `OPENAI_API_KEY` placeholder
- [ ] Remove Phase 1 long-context artifacts (`CorpusLoader`, `CorpusText`, related tests + helpers)
- [ ] Create `service/IngestionService` — reads full PDF via `PagePdfDocumentReader`, chunks with `TokenTextSplitter`, stores via `VectorStore.add()`. Triggered on `ApplicationReadyEvent` if store is empty. TDD.
- [ ] Create `config/ChatClientConfig` — builds `ChatClient` from Anthropic `ChatModel` + `QuestionAnswerAdvisor(vectorStore)`
- [ ] Create `service/AskService` — takes question, calls `ChatClient`, reads token usage from response metadata, computes cost via `CostCalculator`, returns `AnswerResponse`. TDD with mocked `ChatClient`.
- [ ] Create `controller/AskController` — `POST /ask`, `@Valid @RequestBody AskRequest`, delegates to `AskService`, returns `AnswerResponse`. `@WebMvcTest` with `@MockitoBean`.
- [ ] Fix `KnowledgeBotApplicationTests` for Phase 2 context (no CorpusLoader, needs datasource mock or test profile)
- [ ] `docker-compose up --build` — verify: Postgres boots, app boots, ingestion runs, `/ask` reachable on localhost:8080
- [ ] Run sample questions via `curl` (easy, specific, clause-reference, cross-section, fake clause, out-of-corpus); capture JSON responses
- [ ] Write `docs/observations/phase-2-findings.md` — raw Q/A + metrics, contrast with Phase 1
- [ ] Update README `Phase 2 — RAG comparison` section
