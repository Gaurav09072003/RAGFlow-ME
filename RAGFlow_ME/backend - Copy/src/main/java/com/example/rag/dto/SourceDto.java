package com.example.rag.dto;

public record SourceDto(
        String fileName,
        int chunkIndex,
        double score,
        String preview
) {
}
