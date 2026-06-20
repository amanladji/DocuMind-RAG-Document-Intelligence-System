package com.example.rag.api;

import com.example.rag.model.StoredDocument;
import com.example.rag.service.DocumentIngestionService;
import com.example.rag.service.DocumentRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentManagementController {

    private final DocumentRepository documentRepository;
    private final DocumentIngestionService ingestionService;

    public DocumentManagementController(DocumentRepository documentRepository, DocumentIngestionService ingestionService) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
    }

    @GetMapping
    public List<StoredDocument> list(Authentication auth) {
        return documentRepository.findByUserId(auth.getName());
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String documentId, Authentication auth) {
        ingestionService.deleteDocument(auth.getName(), documentId);
    }
}
