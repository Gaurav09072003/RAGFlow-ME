package com.example.rag.dto;

import java.util.List;

public record ChatResponse(
        String answer,
        List<SourceDto> sources
) {
}
