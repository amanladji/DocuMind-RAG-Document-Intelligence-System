package com.example.rag.llm;

import reactor.core.publisher.Flux;

public interface ChatClient {

    String complete(String prompt);

    Flux<String> completeStream(String prompt);
}
