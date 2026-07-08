package cn.project.base.ragpgvector.service;

import cn.project.base.ragpgvector.dto.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

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

    private static final String REWRITE_PROMPT = """
            你是一个搜索优化助手。将用户的自然语言问题改写成更适合向量语义检索的形式。
            要求：
            1. 提取核心关键词和关键概念
            2. 去除口语化表达和冗余
            3. 保持原意不变
            4. 只输出改写后的查询文本，不要多余内容
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Value("${rag.query.rewrite.enabled:true}")
    private boolean rewriteEnabled;

    @Value("${rag.query.rerank.multiplier:3}")
    private int rerankMultiplier;

    @Value("${rag.query.rerank.vector-weight:0.6}")
    private double vectorWeight;

    @Value("${rag.query.context.max-chars:8000}")
    private int maxContextChars;

    public QueryService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    /**
     * Synchronous RAG query: rewrite -> retrieve -> rerank -> answer.
     */
    public QueryResponse query(String message, int topK, double minScore) {
        long start = System.currentTimeMillis();

        // 1. Query rewriting — optimize the search query for better retrieval
        String searchQuery = rewriteEnabled ? rewriteQuery(message) : message;
        if (!searchQuery.equals(message)) {
            log.debug("Query rewritten: '{}' -> '{}'", message, searchQuery);
        }

        // 2. Retrieve more candidates for re-ranking
        int searchTopK = topK * rerankMultiplier;
        List<Document> candidates = searchSimilar(searchQuery, searchTopK, minScore);

        if (candidates.isEmpty()) {
            log.warn("No relevant documents found for: {}", message);
            String fallback = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            return new QueryResponse(fallback, List.of());
        }

        // 3. Re-rank: hybrid score (vector similarity + keyword overlap)
        List<Document> results = rerank(searchQuery, candidates, topK);

        // 4. Build compressed context
        String context = buildContext(results);

        // 5. Generate answer with DeepSeek
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(context))
                .user(message)
                .call()
                .content();

        long elapsed = System.currentTimeMillis() - start;
        log.info("Query completed in {}ms, retrieved {} documents (from {} candidates)",
                elapsed, results.size(), candidates.size());

        return new QueryResponse(answer, buildReferences(searchQuery, results));
    }

    /**
     * Streaming RAG query: rewrite -> retrieve -> rerank -> stream answer.
     */
    public Flux<String> streamQuery(String message, int topK, double minScore) {
        long start = System.currentTimeMillis();

        // 1. Query rewriting
        String searchQuery = rewriteEnabled ? rewriteQuery(message) : message;

        // 2. Retrieve more candidates
        int searchTopK = topK * rerankMultiplier;
        List<Document> candidates = searchSimilar(searchQuery, searchTopK, minScore);

        if (candidates.isEmpty()) {
            log.warn("No relevant documents found for streaming query: {}", message);
            return chatClient.prompt()
                    .user(message)
                    .stream()
                    .content();
        }

        // 3. Re-rank
        List<Document> results = rerank(searchQuery, candidates, topK);

        // 4. Build compressed context
        String context = buildContext(results);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Stream query prepared in {}ms, retrieved {} documents", elapsed, results.size());

        // 5. Stream answer
        return chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(context))
                .user(message)
                .stream()
                .content();
    }

    // ==================== Optimization steps ====================

    /**
     * Rewrite user query for better vector search recall.
     * Uses LLM to extract core keywords and remove noise.
     */
    private String rewriteQuery(String message) {
        try {
            String rewritten = chatClient.prompt()
                    .system(REWRITE_PROMPT)
                    .user(message)
                    .call()
                    .content();
            if (rewritten == null || rewritten.isBlank()) {
                return message;
            }
            return rewritten.strip();
        } catch (Exception e) {
            log.warn("Query rewrite failed, using original query: {}", e.getMessage());
            return message;
        }
    }

    /**
     * Re-rank candidates using hybrid score (vector similarity + keyword overlap).
     * Returns topK documents sorted by hybrid score descending.
     */
    private List<Document> rerank(String query, List<Document> candidates, int topK) {
        if (candidates.size() <= topK) {
            // No re-ranking needed, just compute scores for references
            return candidates;
        }

        // Compute hybrid score for each candidate
        List<Document> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> {
            double sa = computeHybridScore(query, a);
            double sb = computeHybridScore(query, b);
            return Double.compare(sb, sa); // descending
        });

        return sorted.subList(0, topK);
    }

    /**
     * Hybrid score: weighted combination of vector similarity and keyword overlap.
     */
    private double computeHybridScore(String query, Document doc) {
        double distance = (Float) doc.getMetadata().getOrDefault("distance", 1.0f);
        double vectorScore = 1.0 - Math.min(distance, 1.0); // normalize to [0, 1]

        double keywordScore = keywordOverlap(query, doc.getText());

        return vectorWeight * vectorScore + (1.0 - vectorWeight) * keywordScore;
    }

    /**
     * Keyword overlap: fraction of query terms appearing in the document text.
     */
    private double keywordOverlap(String query, String text) {
        String[] queryTerms = query.toLowerCase().split("\\W+");
        if (queryTerms.length == 0) return 0.0;

        String textLower = text.toLowerCase();
        long matchCount = 0;
        for (String term : queryTerms) {
            if (term.length() < 2) continue; // skip single chars
            if (textLower.contains(term)) {
                matchCount++;
            }
        }
        if (matchCount == 0) return 0.0;
        return (double) matchCount / queryTerms.length;
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
     * Build compressed context string from ranked documents.
     * Respects maxContextChars limit.
     */
    private String buildContext(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String source = (String) doc.getMetadata().getOrDefault("source", "unknown");
            String entry = "[" + (i + 1) + "] (" + source + "): " + doc.getText() + "\n\n";

            // Truncate if would exceed limit (always include at least the first)
            if (sb.length() + entry.length() > maxContextChars && !sb.isEmpty()) {
                log.debug("Context truncated at {} chars ({} entries)", sb.length(), i);
                break;
            }
            sb.append(entry);
        }
        return sb.toString();
    }

    /**
     * Build reference list with hybrid scores.
     */
    private List<QueryResponse.Reference> buildReferences(String query, List<Document> documents) {
        List<QueryResponse.Reference> refs = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            double hybridScore = computeHybridScore(query, doc);
            String content = doc.getText();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            refs.add(new QueryResponse.Reference(
                    content,
                    (String) doc.getMetadata().getOrDefault("source", "unknown"),
                    (float) hybridScore
            ));
        }
        return refs;
    }
}
