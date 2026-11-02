package com.example.rag.service;

import com.example.rag.dto.ChatResponse;
import com.example.rag.dto.SourceDto;
import com.example.rag.dto.UploadResponse;
import com.example.rag.model.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RagService {

    private final PdfService pdfService;
    private final ChunkingService chunkingService;
    private final OllamaClient ollamaClient;
    private final DocumentChunkRepository repository;
    private final ObjectMapper mapper;
    private final int topK;
    private final double minScore;

    public RagService(
            PdfService pdfService,
            ChunkingService chunkingService,
            OllamaClient ollamaClient,
            DocumentChunkRepository repository,
            ObjectMapper mapper,
            @Value("${rag.top-k:5}") int topK,
            @Value("${rag.min-score:0.15}") double minScore
    ) {
        this.pdfService = pdfService;
        this.chunkingService = chunkingService;
        this.ollamaClient = ollamaClient;
        this.repository = repository;
        this.mapper = mapper;
        this.topK = topK;
        this.minScore = minScore;
    }

    @Transactional
    public UploadResponse ingest(MultipartFile file) throws IOException {
        String fileName = safeFileName(file.getOriginalFilename());

        PdfService.PdfExtraction extraction = pdfService.extract(file);
        List<String> chunks = chunkingService.split(extraction.text());

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("No usable text chunks were created from the PDF.");
        }

        // Re-uploading the same filename replaces its old chunks.
        repository.deleteByFileName(fileName);

        List<DocumentChunk> entities = new ArrayList<>();

        // Batch embedding prevents one HTTP request per chunk.
        final int batchSize = 16;
        for (int from = 0; from < chunks.size(); from += batchSize) {
            int to = Math.min(from + batchSize, chunks.size());
            List<String> batch = chunks.subList(from, to);
            List<List<Double>> vectors = ollamaClient.embed(batch);

            for (int i = 0; i < batch.size(); i++) {
                String embeddingJson = mapper.writeValueAsString(vectors.get(i));
                entities.add(new DocumentChunk(
                        fileName,
                        from + i,
                        batch.get(i),
                        embeddingJson
                ));
            }
        }

        repository.saveAll(entities);

        return new UploadResponse(fileName, extraction.pages(), chunks.size());
    }

    @Transactional(readOnly = true)
    public List<String> documents() {
        return repository.findDistinctFileNames();
    }

    @Transactional
    public void deleteDocument(String fileName) {
        repository.deleteByFileName(fileName);
    }

    @Transactional(readOnly = true)
    public ChatResponse ask(String question) throws Exception {
        String cleanQuestion = question == null ? "" : question.trim();

        if (cleanQuestion.isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty.");
        }

        List<DocumentChunk> chunks = repository.findAll();
        if (chunks.isEmpty()) {
            throw new IllegalStateException("Upload at least one PDF before asking a question.");
        }

        List<Double> queryVector = ollamaClient.embed(List.of(cleanQuestion)).get(0);

        List<ScoredChunk> ranked = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            List<Double> vector = mapper.readValue(
                    chunk.getEmbeddingJson(),
                    new TypeReference<List<Double>>() {}
            );

            double score = cosineSimilarity(queryVector, vector);
            ranked.add(new ScoredChunk(chunk, score));
        }

        ranked.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        List<ScoredChunk> selected = ranked.stream()
                .filter(item -> item.score() >= minScore)
                .limit(topK)
                .toList();

        // If the configured threshold filters everything, still give the LLM
        // the closest chunks rather than returning a useless empty context.
        if (selected.isEmpty()) {
            selected = ranked.stream().limit(Math.min(3, topK)).toList();
        }

        StringBuilder context = new StringBuilder();
        List<SourceDto> sources = new ArrayList<>();

        for (ScoredChunk item : selected) {
            DocumentChunk c = item.chunk();

            context.append("\n--- SOURCE ---\n")
                    .append("File: ").append(c.getFileName()).append("\n")
                    .append("Chunk: ").append(c.getChunkIndex() + 1).append("\n")
                    .append(c.getContent())
                    .append("\n");

            String preview = c.getContent()
                    .replaceAll("\\s+", " ")
                    .trim();

            if (preview.length() > 180) {
                preview = preview.substring(0, 180) + "...";
            }

            sources.add(new SourceDto(
                    c.getFileName(),
                    c.getChunkIndex() + 1,
                    Math.round(item.score() * 10000.0) / 10000.0,
                    preview
            ));
        }

        String systemPrompt = """
                You are a PDF question-answering assistant.
                Answer ONLY from the supplied CONTEXT.
                Do not invent facts that are not supported by the context.
                If the context does not contain the answer, say:
                "I couldn't find that information in the uploaded PDF."
                Keep the answer clear and useful.
                When useful, mention the source file and chunk number.
                """;

        String userPrompt = """
                QUESTION:
                %s

                CONTEXT:
                %s
                """.formatted(cleanQuestion, context);

        String answer = ollamaClient.chat(systemPrompt, userPrompt);

        return new ChatResponse(answer, sources);
    }

    private String safeFileName(String original) {
        String name = original == null ? "document.pdf" : original.trim();
        name = name.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1);

        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }

        return name.isBlank() ? "document.pdf" : name;
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) {
            throw new IllegalStateException("Embedding dimensions do not match.");
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {
    }
}
