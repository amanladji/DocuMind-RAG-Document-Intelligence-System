package com.example.rag.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "documents")
public class StoredDocument {

    @Id
    private String id;
    private String userId;
    private String fileName;
    private int chunkCount;
    private Instant uploadedAt;

    public StoredDocument() {}

    public StoredDocument(String id, String userId, String fileName, int chunkCount) {
        this.id = id;
        this.userId = userId;
        this.fileName = fileName;
        this.chunkCount = chunkCount;
        this.uploadedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
