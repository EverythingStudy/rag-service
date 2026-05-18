package cn.project.base.agentruntime.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索服务 —— 管理知识库文档的索引与检索。
 * <p>
 * 文档通过 DocumentService 导入后被向量化并存储到 VectorStore，
 * 在 Agent 对话时由 QuestionAnswerAdvisor 自动检索相关片段作为上下文。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;

    public RagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 将文档写入向量存储
     */
    public void storeDocument(String content, Map<String, Object> metadata) {
        Document doc = new Document(content, metadata);
        vectorStore.add(List.of(doc));
        log.info("Stored document: {}", metadata.get("name"));
    }

    /**
     * 批量存储文档
     */
    public void storeDocuments(List<Document> documents) {
        vectorStore.add(documents);
        log.info("Stored {} documents", documents.size());
    }

    /**
     * 检索与查询最相关的文档片段
     */
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build());
    }

    /**
     * 删除所有文档（仅对支持批量删除的 VectorStore 有效）
     */
    public void deleteAll(List<String> docIds) {
        vectorStore.delete(docIds);
        log.info("Deleted {} documents from vector store", docIds.size());
    }
}
