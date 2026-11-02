package com.example.rag.controller;

import com.example.rag.dto.ChatRequest;
import com.example.rag.dto.ChatResponse;
import com.example.rag.dto.UploadResponse;
import com.example.rag.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file)
            throws Exception {
        return ResponseEntity.ok(ragService.ingest(file));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<String>> documents() {
        return ResponseEntity.ok(ragService.documents());
    }

    @DeleteMapping("/documents")
    public ResponseEntity<Map<String, String>> delete(@RequestParam String fileName) {
        ragService.deleteDocument(fileName);
        return ResponseEntity.ok(Map.of("message", "Document deleted."));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request)
            throws Exception {
        return ResponseEntity.ok(ragService.ask(request.question()));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
