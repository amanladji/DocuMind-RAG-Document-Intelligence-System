package com.example.rag.service;

import com.example.rag.config.EmbeddingProperties;
import com.example.rag.config.OpenRouterProperties;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Primary
@Service
public class OpenRouterEmbeddingClient implements EmbeddingClient {

    private final WebClient webClient;
    private final EmbeddingProperties properties;
    private final OpenRouterProperties openRouterProperties;

    public OpenRouterEmbeddingClient(WebClient.Builder webClientBuilder, OpenRouterProperties openRouterProperties, EmbeddingProperties properties) {
        this.properties = properties;
        this.openRouterProperties = openRouterProperties;
        this.webClient = webClientBuilder
                .baseUrl(openRouterProperties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public float[] embed(String text) {
        if (openRouterProperties.apiKey() == null || openRouterProperties.apiKey().isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not configured.");
        }

        EmbeddingRequest request = new EmbeddingRequest(properties.model(), text, properties.dimension());

        EmbeddingResponse response = webClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterProperties.apiKey())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .block();

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("OpenRouter embedding returned an empty response.");
        }

        float[] vector = new float[response.data().getFirst().embedding().length];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = response.data().getFirst().embedding()[i];
        }
        return vector;
    }

    @Override
    public int dimension() {
        return properties.dimension();
    }

    private record EmbeddingRequest(String model, String input, int dimensions) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data, String model, String object) {
    }

    private record EmbeddingData(int index, String object, float[] embedding) {
    }
}
