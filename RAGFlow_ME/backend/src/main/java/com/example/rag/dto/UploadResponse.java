package com.example.rag.dto;

public record UploadResponse(
        String fileName,
        int pages,
        int chunks
) {
}
