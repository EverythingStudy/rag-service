package cn.project.base.ragpgvector.controller;

import cn.project.base.ragpgvector.dto.QueryRequest;
import cn.project.base.ragpgvector.dto.QueryResponse;
import cn.project.base.ragpgvector.service.QueryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/rag/query")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Reactive RAG query — fully non-blocking via WebFlux Mono.
     */
    @PostMapping("/rag-query")
    public Mono<QueryResponse> query(@RequestBody QueryRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Mono.just(new QueryResponse("Query message is required", List.of()));
        }
        return queryService.query(
                request.getMessage(),
                request.getTopK(),
                request.getMinScore()
        );
    }

    /**
     * Reactive RAG query via GET.
     */
    @GetMapping
    public Mono<QueryResponse> queryGet(
            @RequestParam("message") String message,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "minScore", defaultValue = "0.0") double minScore) {
        if (message == null || message.isBlank()) {
            return Mono.just(new QueryResponse("Query message is required", List.of()));
        }
        return queryService.query(message, topK, minScore);
    }

    /**
     * Streaming RAG query — Server-Sent Events (SSE).
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamQuery(@RequestBody QueryRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.empty();
        }
        return queryService.streamQuery(
                request.getMessage(),
                request.getTopK(),
                request.getMinScore()
        );
    }

    /**
     * Streaming RAG query via GET.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamQueryGet(
            @RequestParam("message") String message,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "minScore", defaultValue = "0.0") double minScore) {
        if (message == null || message.isBlank()) {
            return Flux.empty();
        }
        return queryService.streamQuery(message, topK, minScore);
    }
}
