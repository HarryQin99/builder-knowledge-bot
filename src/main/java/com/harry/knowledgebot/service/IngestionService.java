package com.harry.knowledgebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final NamedParameterJdbcTemplate jdbc;
    private final Resource corpus;

    public IngestionService(VectorStore vectorStore,
                            NamedParameterJdbcTemplate jdbc,
                            @Value("${knowledgebot.corpus.path}") Resource corpus) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.corpus = corpus;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            ingest();
        } catch (Exception e) {
            log.error("Ingestion failed [{}]: {} — store may be in partial state; re-run resumes from missing chunks",
                    e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    public IngestionResult ingest() {
        long start = System.currentTimeMillis();
        log.info("Ingestion starting. corpus={}, exists={}, filename={}",
                describeResource(corpus), corpus.exists(), corpus.getFilename());

        long t0 = System.currentTimeMillis();
        var reader = new PagePdfDocumentReader(corpus);
        var pages = reader.get();
        log.info("PDF read: {} pages in {}ms", pages.size(), System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();
        var chunks = TokenTextSplitter.builder().build().apply(pages);
        log.info("Split: {} chunks in {}ms (avg {} chars/chunk)",
                chunks.size(), System.currentTimeMillis() - t1,
                chunks.isEmpty() ? 0 : chunks.stream().mapToInt(c -> c.getText().length()).sum() / chunks.size());

        List<Document> identified = chunks.stream()
                .map(this::withDeterministicId)
                .toList();
        log.info("Assigned {} deterministic IDs (sample: {})",
                identified.size(),
                identified.isEmpty() ? "[]" : identified.get(0).getId());

        long t2 = System.currentTimeMillis();
        Set<UUID> existing = lookupExistingIds(identified);
        log.info("Pre-check: {} of {} IDs already in vector_store ({}ms)",
                existing.size(), identified.size(), System.currentTimeMillis() - t2);

        List<Document> newChunks = identified.stream()
                .filter(d -> !existing.contains(UUID.fromString(d.getId())))
                .toList();

        if (newChunks.isEmpty()) {
            log.info("Nothing new to embed; skipping vectorStore.add()");
        } else {
            long t3 = System.currentTimeMillis();
            log.info("Embedding + inserting {} new chunks via vectorStore.add()...", newChunks.size());
            vectorStore.add(newChunks);
            log.info("vectorStore.add() complete in {}ms", System.currentTimeMillis() - t3);
        }

        long elapsed = System.currentTimeMillis() - start;
        int skipped = identified.size() - newChunks.size();
        log.info("Ingestion complete: {} new chunks embedded, {} already present, {}ms",
                newChunks.size(), skipped, elapsed);

        return new IngestionResult(newChunks.size(), skipped, elapsed);
    }

    private String describeResource(Resource r) {
        try {
            return r.getURI().toString();
        } catch (Exception e) {
            return r.getDescription();
        }
    }

    private Document withDeterministicId(Document chunk) {
        String source = corpus.getFilename() != null ? corpus.getFilename() : "unknown";
        Object page = chunk.getMetadata().getOrDefault("page_number", "");
        String input = source + "|" + page + "|" + chunk.getText();
        UUID id = UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
        return Document.builder()
                .id(id.toString())
                .text(chunk.getText())
                .metadata(chunk.getMetadata())
                .build();
    }

    private Set<UUID> lookupExistingIds(List<Document> chunks) {
        if (chunks.isEmpty()) return Set.of();
        List<UUID> ids = chunks.stream().map(d -> UUID.fromString(d.getId())).toList();
        var params = new MapSqlParameterSource("ids", ids);
        List<UUID> found = jdbc.queryForList(
                "SELECT id FROM vector_store WHERE id IN (:ids)",
                params, UUID.class);
        return new HashSet<>(found);
    }
}
