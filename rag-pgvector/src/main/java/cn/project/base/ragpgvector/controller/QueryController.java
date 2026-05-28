package cn.project.base.ragpgvector.controller;

import cn.project.base.ragpgvector.dto.QueryRequest;
import cn.project.base.ragpgvector.dto.QueryResponse;
import cn.project.base.ragpgvector.service.QueryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/rag/query")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Synchronous RAG query.
     * POST with JSON body: { "message": "...", "topK": 5, "minScore": 0.0 }
     */
    @PostMapping(value = "/rag-query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        QueryResponse response = queryService.query(
                request.getMessage(),
                request.getTopK(),
                request.getMinScore()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Synchronous RAG query via GET (simple usage).
     */
    @GetMapping
    public ResponseEntity<QueryResponse> queryGet(
            @RequestParam("message") String message,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "minScore", defaultValue = "0.0") double minScore) {
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        QueryResponse response = queryService.query(message, topK, minScore);
        return ResponseEntity.ok(response);
    }

    /**
     * Streaming RAG query — returns Server-Sent Events (SSE).
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
