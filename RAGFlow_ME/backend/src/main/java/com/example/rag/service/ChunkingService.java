package com.example.rag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private final int chunkSize;
    private final int overlap;

    public ChunkingService(
            @Value("${rag.chunk-size:1200}") int chunkSize,
            @Value("${rag.chunk-overlap:200}") int overlap
    ) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Invalid RAG chunk configuration.");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<String> split(String text) {
        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < normalized.length()) {
            int targetEnd = Math.min(start + chunkSize, normalized.length());
            int end = findNaturalBreak(normalized, start, targetEnd);

            if (end <= start) {
                end = targetEnd;
            }

            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= normalized.length()) {
                break;
            }

            int nextStart = Math.max(start + 1, end - overlap);

            // Move the overlap start to a word boundary when possible.
            while (nextStart < normalized.length()
                    && nextStart > 0
                    && !Character.isWhitespace(normalized.charAt(nextStart - 1))) {
                nextStart++;
            }

            start = nextStart;
        }

        return chunks;
    }

    private int findNaturalBreak(String text, int start, int targetEnd) {
        if (targetEnd == text.length()) {
            return targetEnd;
        }

        int paragraph = text.lastIndexOf("\n\n", targetEnd);
        if (paragraph > start + chunkSize / 2) {
            return paragraph;
        }

        int sentence = Math.max(
                Math.max(text.lastIndexOf(". ", targetEnd), text.lastIndexOf("? ", targetEnd)),
                text.lastIndexOf("! ", targetEnd)
        );
        if (sentence > start + chunkSize / 2) {
            return sentence + 1;
        }

        int space = text.lastIndexOf(' ', targetEnd);
        if (space > start) {
            return space;
        }

        return targetEnd;
    }
}
