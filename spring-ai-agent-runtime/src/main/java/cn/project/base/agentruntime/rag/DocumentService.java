package cn.project.base.agentruntime.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档导入服务 —— 从 classpath:documents/ 加载文件并写入向量存储。
 * <p>
 * 支持 .txt 和 .md 格式，自动按段落分割后向量化。
 */
@Service
public class DocumentService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final VectorStore vectorStore;
    private final boolean importOnStartup;

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public DocumentService(VectorStore vectorStore,
                           @Value("${agent.import-documents-on-startup:true}") boolean importOnStartup) {
        this.vectorStore = vectorStore;
        this.importOnStartup = importOnStartup;
    }

    @Override
    public void run(String... args) {
        if (importOnStartup) {
            importDocuments();
        }
    }

    /**
     * 从 classpath:documents/ 导入文档到向量存储
     */
    public void importDocuments() {
        try {
            org.springframework.core.io.Resource[] resources =
                    resourceResolver.getResources("classpath:documents/*");

            if (resources.length == 0) {
                log.info("No documents found in classpath:documents/");
                return;
            }

            List<Document> documents = new ArrayList<>();
            for (var resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !(filename.endsWith(".txt") || filename.endsWith(".md"))) {
                    continue;
                }

                log.info("Importing: {}", filename);
                String content = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
                        .lines()
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");

                // 按段落分割
                String[] paragraphs = content.split("\\n\\s*\\n");
                for (int i = 0; i < paragraphs.length; i++) {
                    String text = paragraphs[i].trim();
                    if (!text.isBlank()) {
                        documents.add(new Document(text, Map.of(
                                "name", filename,
                                "paragraph", i
                        )));
                    }
                }
                log.info("Imported {} paragraphs from {}", paragraphs.length, filename);
            }

            if (!documents.isEmpty()) {
                vectorStore.add(documents);
                log.info("Total: {} document chunks stored", documents.size());
            }
        } catch (Exception e) {
            log.error("Failed to import documents", e);
        }
    }
}
