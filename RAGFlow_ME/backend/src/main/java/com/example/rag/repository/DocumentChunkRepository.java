package com.example.rag.repository;

import com.example.rag.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByFileNameOrderByChunkIndexAsc(String fileName);

    @Query("select distinct d.fileName from DocumentChunk d order by d.fileName")
    List<String> findDistinctFileNames();

    @Modifying
    @Query("delete from DocumentChunk d where d.fileName = :fileName")
    int deleteByFileName(@Param("fileName") String fileName);
}
