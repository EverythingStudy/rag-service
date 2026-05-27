package cn.project.base.ragpgvector.service;

import cn.project.base.ragpgvector.dto.IngestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final Executor ingestionExecutor;

    @Value("${rag.ingestion.chunk.max-size:500}")
    private int chunkMaxSize;

    @Value("${rag.ingestion.chunk.overlap:50}")
    private int chunkOverlap;

    @Value("${rag.ingestion.batch.size:50}")
    private int batchSize;

    @Value("${rag.ingestion.parallel:true}")
    private boolean parallelEnabled;

    public DocumentIngestionService(VectorStore vectorStore,
                                    @Qualifier("ingestionExecutor") Executor ingestionExecutor) {
        this.vectorStore = vectorStore;
        this.ingestionExecutor = ingestionExecutor;
    }

    /**
     * Import all documents from a classpath directory (e.g. classpath:documents/*).
     */
    public IngestionResult importFromClasspath(String classpathPattern) {
        try {
            var resourceResolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
            org.springframework.core.io.Resource[] resources = resourceResolver.getResources(classpathPattern);

            if (resources.length == 0) {
                return IngestionResult.error("No documents found at: " + classpathPattern);
            }

            List<FileSource> files = new ArrayList<>();
            for (var resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !isSupportedFile(filename)) continue;

                String content = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
                files.add(new FileSource(filename, content));
            }

            return processFiles(files);
        } catch (Exception e) {
            log.error("Failed to import documents from classpath: {}", classpathPattern, e);
            return IngestionResult.error("Import failed: " + e.getMessage());
        }
    }

    /**
     * Import a single uploaded file.
     */
    public IngestionResult importFile(MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            if (filename == null || !isSupportedFile(filename)) {
                return IngestionResult.error("Unsupported file type: " + filename);
            }
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return processFiles(List.of(new FileSource(filename, content)));
        } catch (Exception e) {
            log.error("Failed to import uploaded file", e);
            return IngestionResult.error("Import failed: " + e.getMessage());
        }
    }

    /**
     * Import multiple uploaded files at once.
     */
    public IngestionResult importFiles(List<MultipartFile> files) {
        List<FileSource> fileSources = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String filename = file.getOriginalFilename();
                if (filename == null || !isSupportedFile(filename)) {
                    errors.add("Unsupported file type: " + filename);
                    continue;
                }
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                fileSources.add(new FileSource(filename, content));
            } catch (Exception e) {
                errors.add("Failed to read " + file.getOriginalFilename() + ": " + e.getMessage());
            }
        }

        IngestionResult result = processFiles(fileSources);
        if (!errors.isEmpty()) {
            List<String> allErrors = new ArrayList<>(errors);
            if (result.getErrors() != null) {
                allErrors.addAll(result.getErrors());
            }
            return IngestionResult.partial(result.getTotalChunks(), result.getProcessedFiles(), allErrors);
        }
        return result;
    }

    /**
     * Core: process multiple files — chunk, embed, store.
     * Uses parallel processing when enabled.
     */
    private IngestionResult processFiles(List<FileSource> files) {
        if (files.isEmpty()) {
            return IngestionResult.error("No valid files to process");
        }

        log.info("Starting ingestion for {} files", files.size());
        long startTime = System.currentTimeMillis();

        // Step 1: chunk all files into documents
        List<Document> allChunks = Collections.synchronizedList(new ArrayList<>());
        List<String> processedFiles = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        if (parallelEnabled && files.size() > 1) {
            // Parallel chunking
            List<CompletableFuture<Void>> futures = files.stream()
                    .map(file -> CompletableFuture.runAsync(() -> {
                        try {
                            List<Document> chunks = chunkDocument(file);
                            allChunks.addAll(chunks);
                            processedFiles.add(file.filename());
                            log.debug("Chunked {} into {} segments", file.filename(), chunks.size());
                        } catch (Exception e) {
                            errors.add("Failed to process " + file.filename() + ": " + e.getMessage());
                            log.error("Error chunking {}", file.filename(), e);
                        }
                    }, ingestionExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } else {
            // Sequential chunking
            for (FileSource file : files) {
                try {
                    List<Document> chunks = chunkDocument(file);
                    allChunks.addAll(chunks);
                    processedFiles.add(file.filename());
                    log.debug("Chunked {} into {} segments", file.filename(), chunks.size());
                } catch (Exception e) {
                    errors.add("Failed to process " + file.filename() + ": " + e.getMessage());
                    log.error("Error chunking {}", file.filename(), e);
                }
            }
        }

        if (allChunks.isEmpty()) {
            return IngestionResult.partial(0, processedFiles, errors.isEmpty()
                    ? List.of("No text content extracted from files")
                    : errors);
        }

        // Step 2: batch insert into vector store
        log.info("Inserting {} chunks into vector store in batches of {}", allChunks.size(), batchSize);
        AtomicInteger batchCount = new AtomicInteger(0);
        for (int i = 0; i < allChunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allChunks.size());
            List<Document> batch = allChunks.subList(i, end);
            try {
                vectorStore.add(batch);
                batchCount.incrementAndGet();
                log.debug("Inserted batch {}/{} ({} docs)", batchCount.get(),
                        (int) Math.ceil((double) allChunks.size() / batchSize), batch.size());
            } catch (Exception e) {
                String msg = "Failed to insert batch " + batchCount.get() + ": " + e.getMessage();
                log.error(msg, e);
                errors.add(msg);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Ingestion completed: {} files, {} chunks, {} batches, {}ms",
                processedFiles.size(), allChunks.size(), batchCount.get(), elapsed);

        if (errors.isEmpty()) {
            return IngestionResult.success(allChunks.size(), processedFiles);
        }
        return IngestionResult.partial(allChunks.size(), processedFiles, errors);
    }

    /**
     * Split a file into text chunks with configurable max size and overlap.
     * Chunks respect paragraph boundaries when possible.
     */
    private List<Document> chunkDocument(FileSource file) {
        String text = file.content().trim();
        if (text.isBlank()) return List.of();

        List<Document> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\s*\\n");

        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isBlank()) continue;

            // If adding this paragraph exceeds max size, finalize current chunk
            if (currentChunk.length() + trimmed.length() + 1 > chunkMaxSize && !currentChunk.isEmpty()) {
                chunks.add(buildChunk(file.filename(), chunkIndex++, currentChunk.toString()));

                // Apply overlap: keep last `chunkOverlap` characters from previous chunk
                String overlap = currentChunk.length() > chunkOverlap
                        ? currentChunk.substring(currentChunk.length() - chunkOverlap)
                        : "";
                currentChunk = new StringBuilder(overlap);
                if (!overlap.isEmpty() && !trimmed.startsWith("\n")) {
                    currentChunk.append("\n");
                }
            }

            if (currentChunk.isEmpty()) {
                currentChunk = new StringBuilder(trimmed);
            } else {
                currentChunk.append("\n\n").append(trimmed);
            }
        }

        // Final chunk
        if (!currentChunk.isEmpty()) {
            chunks.add(buildChunk(file.filename(), chunkIndex, currentChunk.toString()));
        }

        return chunks;
    }

    private Document buildChunk(String filename, int index, String text) {
        return new Document(text, Map.of(
                "source", filename,
                "chunk", index,
                "indexed_at", String.valueOf(System.currentTimeMillis())
        ));
    }

    private boolean isSupportedFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")
                || lower.endsWith(".yaml") || lower.endsWith(".yml")
                || lower.endsWith(".csv") || lower.endsWith(".html") || lower.endsWith(".xml");
    }

    private record FileSource(String filename, String content) {}
}
