package com.harry.knowledgebot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private NamedParameterJdbcTemplate jdbc;

    private final Resource corpus = new ClassPathResource("test-corpus.pdf");
    private IngestionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionService(vectorStore, jdbc, corpus);
    }

    @Test
    void firstBoot_ingestsAllChunks() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of());

        var result = service.ingest();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        assertThat(captor.getValue()).isNotEmpty();
        assertThat(result.newChunks()).isEqualTo(captor.getValue().size());
        assertThat(result.skippedChunks()).isZero();
    }

    @Test
    void reboot_skipsAlreadyIngestedChunks() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of());
        service.ingest();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> firstRun = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(firstRun.capture());
        List<UUID> ingestedIds = firstRun.getValue().stream()
                .map(d -> UUID.fromString(d.getId()))
                .toList();

        reset(vectorStore);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(ingestedIds);

        var result = service.ingest();

        verify(vectorStore, never()).add(any());
        assertThat(result.newChunks()).isZero();
        assertThat(result.skippedChunks()).isEqualTo(ingestedIds.size());
    }

    @Test
    void deterministicIds_sameInputProducesSameIds() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of());

        service.ingest();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> run1 = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(run1.capture());
        List<String> run1Ids = run1.getValue().stream().map(Document::getId).toList();

        reset(vectorStore);
        service.ingest();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> run2 = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(run2.capture());
        List<String> run2Ids = run2.getValue().stream().map(Document::getId).toList();

        assertThat(run1Ids).isEqualTo(run2Ids);
    }
}
