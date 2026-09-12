package com.example.rag.vector;

import com.example.rag.config.VectorStoreProperties;
import com.example.rag.model.DocumentChunk;
import com.example.rag.model.SearchResult;
import com.example.rag.service.EmbeddingClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FileVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(FileVectorStore.class);

    private final VectorStoreProperties properties;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final Path filePath;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<StoredVector> vectors = new ArrayList<>();

    public FileVectorStore(
            VectorStoreProperties properties,
            EmbeddingClient embeddingClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
        this.filePath = Path.of(properties.filePath());
    }

    @PostConstruct
    public void load() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(filePath)) {
                log.info("Vector file does not exist at {}, starting with empty store", filePath);
                return;
            }
            if (Files.size(filePath) == 0) {
                log.info("Vector file is empty at {}, starting with empty store", filePath);
                return;
            }
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                return;
            }
            List<StoredVector> loaded = objectMapper.readValue(bytes, new TypeReference<List<StoredVector>>() {});
            lock.writeLock().lock();
            try {
                vectors.clear();
                vectors.addAll(loaded);
            } finally {
                lock.writeLock().unlock();
            }
            log.info("Loaded {} vectors from {}", vectors.size(), filePath);
        } catch (IOException e) {
            log.warn("Failed to load vectors from {}: {}. Starting with empty store.", filePath, e.getMessage());
        }
    }

    private void persist() {
        lock.readLock().lock();
        List<StoredVector> snapshot;
        try {
            snapshot = new ArrayList<>(vectors);
        } finally {
            lock.readLock().unlock();
        }
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), snapshot);
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), snapshot);
            } catch (IOException ex) {
                log.error("Failed to persist vectors to {}: {}", filePath, ex.getMessage(), ex);
                throw new RuntimeException("Failed to persist vectors", ex);
            }
        }
    }

    @Override
    public void upsert(List<DocumentChunk> chunks, String userId) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<StoredVector> newVectors = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            float[] embedding = embeddingClient.embed(chunk.text());
            StoredVector sv = new StoredVector(
                    chunk.id(),
                    userId,
                    chunk.documentId(),
                    chunk.documentName(),
                    chunk.chunkIndex(),
                    chunk.text(),
                    embedding);
            newVectors.add(sv);
        }
        lock.writeLock().lock();
        try {
            vectors.addAll(newVectors);
        } finally {
            lock.writeLock().unlock();
        }
        persist();
        log.info("Upserted {} chunks for document {} user {}", newVectors.size(),
                chunks.get(0).documentId(), userId);
    }

    @Override
    public List<SearchResult> search(String question, int topK, String userId) {
        float[] queryEmbedding = embeddingClient.embed(question);
        List<ScoredVector> scored = new ArrayList<>();
        lock.readLock().lock();
        try {
            for (StoredVector sv : vectors) {
                if (!sv.userId().equals(userId)) {
                    continue;
                }
                double score = cosineSimilarity(queryEmbedding, sv.embedding());
                scored.add(new ScoredVector(sv, score));
            }
        } finally {
            lock.readLock().unlock();
        }
        scored.sort(Comparator.comparingDouble(ScoredVector::score).reversed());
        int limit = Math.min(topK, scored.size());
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ScoredVector sv = scored.get(i);
            results.add(new SearchResult(
                    sv.vector().documentName(),
                    sv.vector().chunkIndex(),
                    sv.vector().text(),
                    sv.score()));
        }
        return results;
    }

    @Override
    public void deleteByDocumentId(String documentId, String userId) {
        boolean removed;
        lock.writeLock().lock();
        try {
            removed = vectors.removeIf(v -> v.documentId().equals(documentId) && v.userId().equals(userId));
        } finally {
            lock.writeLock().unlock();
        }
        if (removed) {
            persist();
            log.info("Deleted vectors for document {} user {}", documentId, userId);
        }
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredVector(StoredVector vector, double score) {}

    public record StoredVector(
            String id,
            String userId,
            String documentId,
            String documentName,
            int chunkIndex,
            String text,
            float[] embedding) {}
}
