package com.example.rag.vector;

import com.example.rag.model.DocumentChunk;
import com.example.rag.model.SearchResult;
import java.util.List;

public interface VectorStore {

    void upsert(List<DocumentChunk> chunks);

    List<SearchResult> search(String question, int topK);

    void deleteByDocumentId(String documentId);
}
