package com.example.rag.llm;

import com.example.rag.config.OpenRouterProperties;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
public class OpenRouterChatClient implements ChatClient {

    private final WebClient webClient;
    private final OpenRouterProperties properties;

    public OpenRouterChatClient(WebClient.Builder webClientBuilder, OpenRouterProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String complete(String prompt) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not configured.");
        }

        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(),
                properties.temperature(),
                List.of(new Message("user", prompt))
        );

        ChatCompletionResponse response = webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .block();

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenRouter returned an empty response.");
        }

        return response.choices().getFirst().message().content().trim();
    }

    @Override
    public Flux<String> completeStream(String prompt) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return Flux.error(new IllegalStateException("OPENROUTER_API_KEY is not configured."));
        }

        StreamingRequest request = new StreamingRequest(
                properties.model(),
                properties.temperature(),
                true,
                List.of(new Message("user", prompt))
        );

        return webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .filter(Objects::nonNull)
                .filter(event -> event.data() != null)
                .filter(event -> !"[DONE]".equals(event.data()))
                .map(event -> parseStreamContent(event.data()))
                .filter(content -> !content.isEmpty());
    }

    private String parseStreamContent(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            var choice = node.path("choices").get(0);
            if (choice == null) return "";
            return choice.path("delta").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private record ChatCompletionRequest(
            String model,
            double temperature,
            List<Message> messages
    ) {
    }

    private record StreamingRequest(
            String model,
            double temperature,
            boolean stream,
            List<Message> messages
    ) {
    }

    private record Message(String role, String content) {
    }

    private record ChatCompletionResponse(List<Choice> choices) {
    }

    private record Choice(Message message) {
    }
}
