package com.example.rag.vector;

import com.example.rag.model.DocumentChunk;
import com.example.rag.model.SearchResult;
import java.util.List;

public interface VectorStore {

    void upsert(List<DocumentChunk> chunks, String userId);

    List<SearchResult> search(String question, int topK, String userId);

    void deleteByDocumentId(String documentId, String userId);
}
