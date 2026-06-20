package com.example.rag.vector;

import com.example.rag.config.QdrantProperties;
import com.example.rag.model.DocumentChunk;
import com.example.rag.model.SearchResult;
import com.example.rag.service.EmbeddingClient;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final WebClient webClient;
    private final QdrantProperties properties;
    private final EmbeddingClient embeddingClient;

    public QdrantVectorStore(
            WebClient.Builder webClientBuilder,
            QdrantProperties properties,
            EmbeddingClient embeddingClient
    ) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        var builder = webClientBuilder.baseUrl(properties.baseUrl());
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.defaultHeader("api-key", properties.apiKey());
        }
        this.webClient = builder.build();
    }

    @PostConstruct
    public void ensureCollection() {
        boolean exists = true;
        try {
            webClient.get()
                    .uri("/collections/{collection}", properties.collectionName())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException.NotFound ignored) {
            exists = false;
        }

        if (!exists) {
            CreateCollectionRequest request = new CreateCollectionRequest(
                    new VectorParams(embeddingClient.dimension(), "Cosine")
            );
            webClient.put()
                    .uri("/collections/{collection}", properties.collectionName())
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        }

        ensurePayloadIndex("documentId", "keyword");
    }

    private void ensurePayloadIndex(String fieldName, String fieldType) {
        try {
            webClient.put()
                    .uri("/collections/{collection}/index", properties.collectionName())
                    .bodyValue(Map.of("field_name", fieldName, "field_type", fieldType))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Created payload index on {} ({})", fieldName, fieldType);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 400) {
                log.info("Payload index on {} already exists", fieldName);
            } else {
                log.warn("Failed to create payload index on {}: {}", fieldName, e.getMessage());
            }
        }
    }

    @Override
    public void upsert(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        List<Point> points = chunks.stream()
                .map(chunk -> new Point(
                        chunk.id(),
                        embeddingClient.embed(chunk.text()),
                        Map.of(
                                "documentId", chunk.documentId(),
                                "documentName", chunk.documentName(),
                                "chunkIndex", chunk.chunkIndex(),
                                "text", chunk.text()
                        )
                ))
                .toList();

        webClient.put()
                .uri("/collections/{collection}/points?wait=true", properties.collectionName())
                .bodyValue(new UpsertPointsRequest(points))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public List<SearchResult> search(String question, int topK) {
        SearchRequest request = new SearchRequest(
                embeddingClient.embed(question),
                topK,
                true
        );

        SearchResponse response = webClient.post()
                .uri("/collections/{collection}/points/search", properties.collectionName())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SearchResponse.class)
                .block();

        if (response == null || response.result() == null) {
            return List.of();
        }

        return response.result().stream()
                .map(point -> new SearchResult(
                        String.valueOf(point.payload().get("documentName")),
                        ((Number) point.payload().get("chunkIndex")).intValue(),
                        String.valueOf(point.payload().get("text")),
                        point.score()
                ))
                .toList();
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        webClient.post()
                .uri("/collections/{collection}/points/delete?wait=true", properties.collectionName())
                .bodyValue(Map.of("filter", Map.of(
                        "must", List.of(Map.of(
                                "key", "documentId",
                                "match", Map.of("value", documentId)
                        ))
                )))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private record CreateCollectionRequest(VectorParams vectors) {
    }

    private record VectorParams(int size, String distance) {
    }

    private record UpsertPointsRequest(List<Point> points) {
    }

    private record Point(String id, float[] vector, Map<String, Object> payload) {
    }

    private record SearchRequest(float[] vector, int limit, boolean with_payload) {
    }

    private record SearchResponse(List<ScoredPoint> result) {
    }

    private record ScoredPoint(double score, Map<String, Object> payload) {
    }
}
