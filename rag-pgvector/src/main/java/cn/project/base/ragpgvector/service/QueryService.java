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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
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

    @Value("${rag.query.context.max-chars:4000}")
    private int maxContextChars;

    @Value("${rag.query.context.max-docs:3}")
    private int maxContextDocs;

    public QueryService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    /**
     * Reactive RAG query: rewrite → retrieve → rerank → answer.
     * Fully non-blocking: LLM calls use streaming, DB calls wrapped via fromCallable.
     */
    public Mono<QueryResponse> query(String message, int topK, double minScore) {
        long start = System.currentTimeMillis();

        // 1. Query rewriting (reactive)
        Mono<String> searchQueryMono = rewriteEnabled
                ? rewriteQuery(message).defaultIfEmpty(message)
                : Mono.just(message);

        return searchQueryMono.flatMap(searchQuery -> {
            if (!searchQuery.equals(message)) {
                log.debug("Query rewritten: '{}' -> '{}'", message, searchQuery);
            }

            // 2. Retrieve candidates (JDBC call wrapped to avoid blocking the reactive pipeline)
            int searchTopK = topK * rerankMultiplier;
            Mono<List<Document>> candidatesMono = searchSimilarReactive(searchQuery, searchTopK, minScore);

            return candidatesMono.flatMap(candidates -> {
                if (candidates.isEmpty()) {
                    log.warn("No relevant documents found for: {}", message);
                    // Fallback: stream LLM without context
                    return chatClient.prompt()
                            .user(message)
                            .stream()
                            .content()
                            .collectList()
                            .map(list -> {
                                long elapsed = System.currentTimeMillis() - start;
                                log.info("Query completed in {}ms (no context)", elapsed);
                                return new QueryResponse(String.join("", list), List.of());
                            });
                }

                // 3. Re-rank (pure CPU, no blocking)
                List<Document> results = rerank(searchQuery, candidates, topK);

                // 4. Build compressed context (pure CPU)
                String context = buildContext(results);
                List<QueryResponse.Reference> refs = buildReferences(searchQuery, results);

                // 5. Stream answer and collect into full response
                long elapsed = System.currentTimeMillis() - start;
                log.info("Query prepared in {}ms, retrieved {} documents (from {} candidates)",
                        elapsed, results.size(), candidates.size());

                return chatClient.prompt()
                        .system(SYSTEM_PROMPT.formatted(context))
                        .user(message)
                        .stream()
                        .content()
                        .collectList()
                        .map(list -> {
                            long totalElapsed = System.currentTimeMillis() - start;
                            log.info("Query completed in {}ms", totalElapsed);
                            return new QueryResponse(String.join("", list), refs);
                        });
            });
        });
    }

    /**
     * Streaming RAG query: rewrite → retrieve → rerank → stream answer.
     */
    public Flux<String> streamQuery(String message, int topK, double minScore) {
        long start = System.currentTimeMillis();

        // 1. Query rewriting
        String searchQuery = rewriteEnabled ? rewriteQuery(message).block() : message;
        if (searchQuery == null) searchQuery = message;

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

    // ==================== Reactive steps ====================

    /**
     * Rewrite user query via LLM, returns Mono for reactive composition.
     */
    private Mono<String> rewriteQuery(String message) {
        return chatClient.prompt()
                .system(REWRITE_PROMPT)
                .user(message)
                .stream()
                .content()
                .collectList()
                .map(list -> String.join("", list).strip())
                .map(rewritten -> rewritten.isBlank() ? message : rewritten)
                .onErrorResume(e -> {
                    log.warn("Query rewrite failed, using original query: {}", e.getMessage());
                    return Mono.just(message);
                });
    }

    /**
     * Reactive wrapper around blocking vector store JDBC call.
     * Offloads to bounded-elastic scheduler to avoid blocking Netty event loop.
     */
    private Mono<List<Document>> searchSimilarReactive(String query, int topK, double minScore) {
        return Mono.fromCallable(() -> searchSimilar(query, topK, minScore))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== Original blocking steps ====================

    /**
     * Re-rank candidates using hybrid score (vector similarity + keyword overlap).
     * Returns topK documents sorted by hybrid score descending.
     */
    private List<Document> rerank(String query, List<Document> candidates, int topK) {
        if (candidates.size() <= topK) {
            return candidates;
        }

        List<Document> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> {
            double sa = computeHybridScore(query, a);
            double sb = computeHybridScore(query, b);
            return Double.compare(sb, sa);
        });

        return sorted.subList(0, topK);
    }

    /**
     * Hybrid score: weighted combination of vector similarity and keyword overlap.
     */
    private double computeHybridScore(String query, Document doc) {
        double distance = (Float) doc.getMetadata().getOrDefault("distance", 1.0f);
        double vectorScore = 1.0 - Math.min(distance, 1.0);
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
            if (term.length() < 2) continue;
            if (textLower.contains(term)) {
                matchCount++;
            }
        }
        if (matchCount == 0) return 0.0;
        return (double) matchCount / queryTerms.length;
    }

    /**
     * Search vector store for similar documents (blocking JDBC call).
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
     * Limits doc count, allocates token budget proportionally by score.
     */
    private String buildContext(List<Document> documents) {
        List<Document> docs = documents.size() > maxContextDocs
                ? documents.subList(0, maxContextDocs)
                : documents;

        double totalScore = 0;
        int[] charBudgets = new int[docs.size()];
        int headerOverhead = docs.size() * 20;
        int availableChars = maxContextChars - headerOverhead;

        double[] weights = new double[docs.size()];
        for (int i = 0; i < docs.size(); i++) {
            float dist = (Float) docs.get(i).getMetadata().getOrDefault("distance", 0.5f);
            weights[i] = 1.0 - dist;
            totalScore += weights[i];
        }

        int allocated = 0;
        for (int i = 0; i < docs.size(); i++) {
            if (i == docs.size() - 1) {
                charBudgets[i] = Math.max(200, availableChars - allocated);
            } else {
                int budget = (int) (availableChars * (weights[i] / totalScore));
                budget = Math.max(200, budget);
                charBudgets[i] = budget;
                allocated += budget;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String source = (String) doc.getMetadata().getOrDefault("source", "unknown");
            String text = doc.getText();

            if (text.length() > charBudgets[i]) {
                text = text.substring(0, charBudgets[i]) + "...";
            }

            sb.append("[").append(i + 1).append("] (").append(source).append("): ")
                    .append(text).append("\n\n");
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
