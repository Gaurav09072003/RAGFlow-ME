package com.example.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OllamaClient {

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String chatModel;
    private final String embeddingModel;

    public OllamaClient(
            RestClient client,
            ObjectMapper mapper,
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:llama3.2:3b}") String chatModel,
            @Value("${ollama.embedding-model:nomic-embed-text}") String embeddingModel
    ) {
        this.client = client;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    public List<List<Double>> embed(List<String> texts) {
        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", texts
        );

        JsonNode response = post("/api/embed", body);

        JsonNode embeddings = response.path("embeddings");
        if (!embeddings.isArray() || embeddings.size() != texts.size()) {
            throw new IllegalStateException("Ollama returned an invalid embeddings response.");
        }

        List<List<Double>> result = new ArrayList<>();
        for (JsonNode vectorNode : embeddings) {
            List<Double> vector = new ArrayList<>();
            for (JsonNode value : vectorNode) {
                vector.add(value.asDouble());
            }
            result.add(vector);
        }
        return result;
    }

    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> system = Map.of(
                "role", "system",
                "content", systemPrompt
        );
        Map<String, Object> user = Map.of(
                "role", "user",
                "content", userPrompt
        );

        Map<String, Object> body = new HashMap<>();
        body.put("model", chatModel);
        body.put("messages", List.of(system, user));
        body.put("stream", false);
        body.put("options", Map.of(
                "temperature", 0.2,
                "num_ctx", 8192
        ));

        JsonNode response = post("/api/chat", body);
        String answer = response.path("message").path("content").asText();

        if (answer.isBlank()) {
            throw new IllegalStateException("Ollama returned an empty answer.");
        }

        return answer.trim();
    }

    private JsonNode post(String path, Object body) {
        try {
            String raw = client.post()
                    .uri(baseUrl + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Ollama returned an empty response.");
            }

            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not connect to Ollama at " + baseUrl +
                    ". Make sure Ollama is running and the required models are installed.",
                    e
            );
        }
    }
}
