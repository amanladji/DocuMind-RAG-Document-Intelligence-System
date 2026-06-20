package com.example.rag.api;

import com.example.rag.api.dto.AskRequest;
import com.example.rag.api.dto.AskResponse;
import com.example.rag.api.dto.UploadResponse;
import com.example.rag.service.DocumentIngestionService;
import com.example.rag.service.QuestionAnsweringService;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final QuestionAnsweringService questionAnsweringService;

    public DocumentController(
            DocumentIngestionService ingestionService,
            QuestionAnsweringService questionAnsweringService
    ) {
        this.ingestionService = ingestionService;
        this.questionAnsweringService = questionAnsweringService;
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

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody AskRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        sseExecutor.execute(() -> {
            try {
                Flux<String> stream = questionAnsweringService.answerStream(request);
                stream.subscribe(
                    data -> {
                        try {
                            emitter.send(SseEmitter.event().data(data));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    error -> {
                        try {
                            String errorJson = "{\"content\":\"[Error: " + error.getMessage() + "]\",\"sources\":null}";
                            emitter.send(SseEmitter.event().data(errorJson));
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    },
                    () -> {
                        try {
                            emitter.complete();
                        } catch (Exception e) {
                        }
                    }
                );
            } catch (Exception e) {
                try {
                    String errorJson = "{\"content\":\"[Error: " + e.getMessage() + "]\",\"sources\":null}";
                    emitter.send(SseEmitter.event().data(errorJson));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    @PreDestroy
    void shutdown() {
        sseExecutor.shutdownNow();
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Resource> favicon() {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(new ClassPathResource("static/favicon.svg"));
    }
}
