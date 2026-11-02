package com.example.rag.model;

import jakarta.persistence.*;

@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "idx_chunk_filename", columnList = "fileName")
})
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String content;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String embeddingJson;

    public DocumentChunk() {
    }

    public DocumentChunk(String fileName, Integer chunkIndex, String content, String embeddingJson) {
        this.fileName = fileName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embeddingJson = embeddingJson;
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public String getEmbeddingJson() {
        return embeddingJson;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setEmbeddingJson(String embeddingJson) {
        this.embeddingJson = embeddingJson;
    }
}
