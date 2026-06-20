package com.example.rag.api;

import com.example.rag.api.dto.AskRequest;
import com.example.rag.api.dto.AskResponse;
import com.example.rag.api.dto.SuggestionsResponse;
import com.example.rag.api.dto.UploadResponse;
import com.example.rag.model.SearchResult;
import com.example.rag.service.DocumentIngestionService;
import com.example.rag.service.QuestionAnsweringService;
import com.example.rag.service.SuggestionService;
import com.example.rag.vector.VectorStore;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final QuestionAnsweringService questionAnsweringService;
    private final SuggestionService suggestionService;
    private final VectorStore vectorStore;
    private final com.example.rag.config.RagProperties properties;

    public DocumentController(
            DocumentIngestionService ingestionService,
            QuestionAnsweringService questionAnsweringService,
            SuggestionService suggestionService,
            VectorStore vectorStore,
            com.example.rag.config.RagProperties properties
    ) {
        this.ingestionService = ingestionService;
        this.questionAnsweringService = questionAnsweringService;
        this.suggestionService = suggestionService;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestPart("file") MultipartFile file, Authentication auth) throws IOException {
        return ingestionService.ingest(file, auth.getName());
    }

    @PostMapping(value = "/upload/multi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<UploadResponse> uploadMultiple(@RequestPart("files") List<MultipartFile> files, Authentication auth) throws IOException {
        return files.stream()
                .map(f -> {
                    try {
                        return ingestionService.ingest(f, auth.getName());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return questionAnsweringService.answer(request);
    }

    @PostMapping(value = "/suggestions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SuggestionsResponse suggestions(@Valid @RequestBody AskRequest request) {
        int topK = request.topK() == null ? properties.defaultTopK() : request.topK();
        List<SearchResult> results = vectorStore.search(request.question(), topK).stream()
                .filter(r -> r.score() >= properties.minRelevanceScore())
                .toList();
        List<String> suggestions = suggestionService.generate(request.question(), results);
        return new SuggestionsResponse(suggestions);
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Resource> favicon() {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(new ClassPathResource("static/favicon.svg"));
    }
}
