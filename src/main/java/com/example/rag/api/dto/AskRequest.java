package com.example.rag.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AskRequest(
        @NotBlank String question,
        @Min(1) @Max(20) Integer topK,
        List<HistoryMessage> history
) {
    public record HistoryMessage(String role, String content) {}
}
