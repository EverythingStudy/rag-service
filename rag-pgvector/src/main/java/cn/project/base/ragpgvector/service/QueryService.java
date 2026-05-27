package cn.project.base.ragpgvector.service;

import cn.project.base.ragpgvector.dto.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private static final String SYSTEM_PROMPT = """
            你是一个智能RAG知识库助手。请基于以下检索到的参考信息来回答用户的问题。

            要求：
            1. 如果参考信息足够回答，请给出准确、简洁的回答
            2. 如果参考信息不足以回答问题，请告知用户知识库中缺少相关信息
            3. 不要编造不在参考信息中的内容
            4. 引用相关来源（文件名）来增强可信度
            5. 使用中文回答

            参考信息：
            %s
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public QueryService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    /**
     * Synchronous RAG query: retrieve context -> LLM answer.
     */
    public QueryResponse query(String message, int topK, double minScore) {
        long start = System.currentTimeMillis();

        // 1. Search vector store
        List<Document> results = searchSimilar(message, topK, minScore);

        if (results.isEmpty()) {
            log.warn("No relevant documents found for: {}", message);
            String fallback = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            return new QueryResponse(fallback, List.of());
        }

        // 2. Build context from retrieved documents
        String context = buildContext(results);

        // 3. Generate answer with DeepSeek
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(context))
                .user(message)
                .call()
                .content();

        // 4. Build response with references
        List<QueryResponse.Reference> refs = results.stream()
                .map(doc -> new QueryResponse.Reference(
                        doc.getText().length() > 200
                                ? doc.getText().substring(0, 200) + "..."
                                : doc.getText(),
                        (String) doc.getMetadata().getOrDefault("source", "unknown"),
                        (double) doc.getMetadata().getOrDefault("distance", 0.0)
                ))
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        log.info("Query completed in {}ms, retrieved {} documents", elapsed, results.size());

        return new QueryResponse(answer, refs);
    }

    /**
     * Streaming RAG query: retrieve context -> stream LLM answer token by token.
     */
    public Flux<String> streamQuery(String message, int topK, double minScore) {
        long start = System.currentTimeMillis();

        // 1. Search vector store
        List<Document> results = searchSimilar(message, topK, minScore);

        if (results.isEmpty()) {
            log.warn("No relevant documents found for streaming query: {}", message);
            return chatClient.prompt()
                    .user(message)
                    .stream()
                    .content();
        }

        // 2. Build context
        String context = buildContext(results);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Stream query prepared in {}ms, retrieved {} documents", elapsed, results.size());

        // 3. Stream answer
        return chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(context))
                .user(message)
                .stream()
                .content();
    }

    /**
     * Search vector store for similar documents.
     */
    private List<Document> searchSimilar(String message, int topK, double minScore) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(message)
                .topK(topK)
                .similarityThreshold(minScore)
                .build());
    }

    /**
     * Build context string from list of documents.
     */
    private String buildContext(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String source = (String) doc.getMetadata().getOrDefault("source", "unknown");
            sb.append("[").append(i + 1).append("] ").append("(").append(source).append("): ");
            sb.append(doc.getText()).append("\n\n");
        }
        return sb.toString();
    }
}
