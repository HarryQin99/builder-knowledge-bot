package com.harry.knowledgebot.service;

public record IngestionResult(int newChunks, int skippedChunks, long elapsedMs) {}
