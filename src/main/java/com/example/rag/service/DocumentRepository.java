package com.example.rag.service;

import com.example.rag.model.StoredDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DocumentRepository extends MongoRepository<StoredDocument, String> {
    List<StoredDocument> findByUserId(String userId);
    Optional<StoredDocument> findByUserIdAndId(String userId, String id);
    void deleteByUserIdAndId(String userId, String id);
}
