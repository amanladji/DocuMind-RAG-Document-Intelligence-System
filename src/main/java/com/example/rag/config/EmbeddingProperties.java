package com.example.rag.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "embedding")
public record EmbeddingProperties(@Positive int dimension, @NotBlank String model) {
}
