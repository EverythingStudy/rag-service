package cn.project.base.ragpgvector.controller;

import cn.project.base.ragpgvector.dto.IngestionResult;
import cn.project.base.ragpgvector.service.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/rag/document")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Upload and ingest a single document file.
     */
    @PostMapping("/upload")
    public ResponseEntity<IngestionResult> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(IngestionResult.error("File is empty"));
        }
        IngestionResult result = ingestionService.importFile(file);
        return ResponseEntity.ok(result);
    }

    /**
     * Upload and ingest multiple documents at once.
     */
    @PostMapping("/upload/batch")
    public ResponseEntity<IngestionResult> uploadDocuments(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(IngestionResult.error("No files provided"));
        }
        IngestionResult result = ingestionService.importFiles(files);
        return ResponseEntity.ok(result);
    }

    /**
     * Import documents from classpath directory (e.g. classpath:documents/*).
     */
    @PostMapping("/import")
    public ResponseEntity<IngestionResult> importFromClasspath(
            @RequestParam(value = "path", defaultValue = "classpath:documents/*") String path) {
        IngestionResult result = ingestionService.importFromClasspath(path);
        return ResponseEntity.ok(result);
    }
}
