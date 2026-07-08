package cn.project.base.ragpgvector.service;

import cn.project.base.ragpgvector.dto.IngestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final Executor ingestionExecutor;
    private final EmbeddingModel embeddingModel;

    @Value("${rag.ingestion.chunk.max-size:500}")
    private int chunkMaxSize;

    @Value("${rag.ingestion.chunk.overlap:50}")
    private int chunkOverlap;

    @Value("${rag.ingestion.chunk.topic-threshold:0.7}")
    private double topicThreshold;

    @Value("${rag.ingestion.batch.size:50}")
    private int batchSize;

    @Value("${rag.ingestion.parallel:true}")
    private boolean parallelEnabled;

    @Value("${rag.ingestion.max-concurrent:2}")
    private int maxConcurrent;

    private Semaphore concurrencySemaphore;

    public DocumentIngestionService(VectorStore vectorStore,
                                    @Qualifier("ingestionExecutor") Executor ingestionExecutor,
                                    EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.ingestionExecutor = ingestionExecutor;
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    public void init() {
        this.concurrencySemaphore = new Semaphore(maxConcurrent);
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

                try (InputStream is = resource.getInputStream()) {
                    String content = extractText(filename, is);
                    files.add(new FileSource(filename, content));
                }
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
            try (InputStream is = file.getInputStream()) {
                String content = extractText(filename, is);
                return processFiles(List.of(new FileSource(filename, content)));
            }
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
                try (InputStream is = file.getInputStream()) {
                    String content = extractText(filename, is);
                    fileSources.add(new FileSource(filename, content));
                }
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
     * Flushes each file's chunks to vector store immediately (per-file release).
     * Uses parallel processing with semaphore-based concurrency limiting.
     */
    private IngestionResult processFiles(List<FileSource> files) {
        if (files.isEmpty()) {
            return IngestionResult.error("No valid files to process");
        }

        log.info("Starting ingestion for {} files", files.size());
        long startTime = System.currentTimeMillis();

        List<String> processedFiles = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger totalChunks = new AtomicInteger(0);

        if (parallelEnabled && files.size() > 1) {
            // Parallel: semaphore limits concurrent file processing
            List<CompletableFuture<Void>> futures = files.stream()
                    .map(file -> CompletableFuture.runAsync(() -> {
                        try {
                            concurrencySemaphore.acquire();
                            try {
                                int n = processSingleFile(file, processedFiles, errors);
                                totalChunks.addAndGet(n);
                            } finally {
                                concurrencySemaphore.release();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            errors.add("Interrupted: " + file.filename());
                        }
                    }, ingestionExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } else {
            // Sequential: one file at a time
            for (FileSource file : files) {
                int n = processSingleFile(file, processedFiles, errors);
                totalChunks.addAndGet(n);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Ingestion completed: {} files, {} chunks, {}ms",
                processedFiles.size(), totalChunks.get(), elapsed);

        if (errors.isEmpty()) {
            return IngestionResult.success(totalChunks.get(), processedFiles);
        }
        return IngestionResult.partial(totalChunks.get(), processedFiles, errors);
    }

    /**
     * Chunk a single file and flush chunks to vector store immediately.
     * This avoids holding all chunks from all files in memory at once.
     */
    private int processSingleFile(FileSource file,
                                  List<String> processedFiles,
                                  List<String> errors) {
        try {
            List<Document> chunks = chunkDocument(file);
            if (chunks.isEmpty()) return 0;

            // Immediate flush: write this file's chunks to vector store
            for (int i = 0; i < chunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chunks.size());
                vectorStore.add(chunks.subList(i, end));
            }

            processedFiles.add(file.filename());
            log.debug("Processed {} ({} chunks)", file.filename(), chunks.size());
            return chunks.size();
        } catch (Exception e) {
            errors.add("Failed to process " + file.filename() + ": " + e.getMessage());
            log.error("Error processing {}", file.filename(), e);
            return 0;
        }
    }

    /**
     * Split a file into text chunks using dynamic window + topic drift detection.
     *
     * Chunk boundaries are triggered by either:
     * 1. Topic drift — cosine similarity between current paragraph and window center < threshold
     * 2. Size limit — adding the paragraph would exceed chunkMaxSize
     *
     * Paragraph embeddings are batch-computed via the configured EmbeddingModel (DashScope).
     */
    private List<Document> chunkDocument(FileSource file) {
        String text = file.content().trim();
        if (text.isBlank()) return List.of();

        String[] rawParagraphs = text.split("\\n\\s*\\n");
        List<String> paragraphs = new ArrayList<>();
        for (String p : rawParagraphs) {
            String trimmed = p.trim();
            if (!trimmed.isBlank()) paragraphs.add(trimmed);
        }
        if (paragraphs.isEmpty()) return List.of();

        // Single paragraph — skip embedding call, return single chunk
        if (paragraphs.size() == 1) {
            return List.of(buildChunk(file.filename(), 0, paragraphs.get(0)));
        }

        // Batch compute embeddings for all paragraphs
        List<float[]> embeddings = computeEmbeddings(paragraphs);

        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;

        // Current window state
        List<String> currentParas = new ArrayList<>();
        currentParas.add(paragraphs.get(0));
        float[] centerEmb = embeddings.get(0);
        int charSize = paragraphs.get(0).length();

        for (int i = 1; i < paragraphs.size(); i++) {
            String para = paragraphs.get(i);
            float[] paraEmb = embeddings.get(i);
            boolean shouldSplit = false;

            // 1. Hard limit: would exceed max chunk size
            if (charSize + 2 + para.length() > chunkMaxSize) {
                shouldSplit = true;
            }

            // 2. Topic drift: similarity below threshold
            if (!shouldSplit) {
                double sim = cosineSimilarity(centerEmb, paraEmb);
                if (sim < topicThreshold) {
                    shouldSplit = true;
                }
            }

            if (shouldSplit) {
                String chunkText = String.join("\n\n", currentParas);
                chunks.add(buildChunk(file.filename(), chunkIndex++, chunkText));

                // Overlap: carry last `chunkOverlap` characters as text prefix
                String overlap = chunkText.length() > chunkOverlap
                        ? chunkText.substring(chunkText.length() - chunkOverlap) + "\n\n"
                        : "";

                // Start new window with overlap prefix + current paragraph
                currentParas = new ArrayList<>();
                currentParas.add(overlap + para);

                // Reset center to current paragraph (overlap text doesn't affect topic detection)
                centerEmb = paraEmb;
                charSize = overlap.length() + para.length();
            } else {
                currentParas.add(para);
                // Update running average center
                centerEmb = runningAverage(centerEmb, paraEmb, currentParas.size() - 1, currentParas.size());
                charSize += 2 + para.length();
            }
        }

        // Final chunk
        if (!currentParas.isEmpty()) {
            chunks.add(buildChunk(file.filename(), chunkIndex, String.join("\n\n", currentParas)));
        }

        return chunks;
    }

    /**
     * Batch-compute embeddings for all paragraphs in one API call.
     */
    private List<float[]> computeEmbeddings(List<String> texts) {
        List<float[]> raw = embeddingModel.embed(texts);
        List<float[]> result = new ArrayList<>(raw.size());
        for (float[] vec : raw) {
            result.add(vec);
        }
        return result;
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Incremental running average: newCenter = (oldCenter * oldCount + newVec) / newCount
     */
    private static float[] runningAverage(float[] center, float[] newVec, int oldCount, int newCount) {
        float[] result = new float[center.length];
        double ratio = (double) oldCount / newCount;
        for (int i = 0; i < center.length; i++) {
            result[i] = (float) (center[i] * ratio + newVec[i] / newCount);
        }
        return result;
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
                || lower.endsWith(".csv") || lower.endsWith(".html") || lower.endsWith(".xml")
                || lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".xlsx");
    }

    /**
     * Extract text content from a file based on its extension.
     * Accepts an InputStream for streaming-compatible parsing.
     * Supports: plain text, PDF, Word (.docx), Excel (.xlsx).
     */
    private String extractText(String filename, InputStream stream) throws IOException {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return extractTextFromPdf(stream);
        } else if (lower.endsWith(".docx")) {
            return extractTextFromDocx(stream);
        } else if (lower.endsWith(".xlsx")) {
            return extractTextFromXlsx(stream);
        } else {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractTextFromPdf(InputStream stream) throws IOException {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(stream))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String extractTextFromDocx(InputStream stream) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(stream)) {
            return doc.getParagraphs().stream()
                    .map(p -> p.getText().trim())
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.joining("\n\n"));
        }
    }

    /**
     * Streaming SAX-based XLSX parser — avoids loading the full workbook DOM.
     * Processes each sheet as a stream of XML events via XSSFSheetXMLHandler.
     */
    private String extractTextFromXlsx(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try {
            OPCPackage pkg = OPCPackage.open(stream);
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();

            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-call", true);
            SAXParser parser = factory.newSAXParser();

            XSSFSheetXMLHandler.SheetContentsHandler handler =
                    new XSSFSheetXMLHandler.SheetContentsHandler() {
                        @Override
                        public void startRow(int rowNum) { }

                        @Override
                        public void endRow(int rowNum) {
                            sb.append('\n');
                        }

                        @Override
                        public void cell(String cellRef, String formattedValue, XSSFComment comment) {
                            if (formattedValue != null && !formattedValue.trim().isEmpty()) {
                                sb.append(formattedValue.trim()).append(' ');
                            }
                        }
                    };

            XSSFSheetXMLHandler xmlHandler = new XSSFSheetXMLHandler(
                    styles, sst, handler, new DataFormatter(), false);

            Iterator<InputStream> sheets = reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheetStream = sheets.next()) {
                    parser.parse(sheetStream, xmlHandler);
                }
            }

            pkg.close();
        } catch (Exception e) {
            throw new IOException("Failed to parse XLSX", e);
        }
        return sb.toString().trim();
    }

    private record FileSource(String filename, String content) {}
}
