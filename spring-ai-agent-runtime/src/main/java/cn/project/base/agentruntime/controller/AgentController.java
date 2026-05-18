package cn.project.base.agentruntime.controller;

import cn.project.base.agentruntime.agent.AgentService;
import cn.project.base.agentruntime.domain.AgentResult;
import cn.project.base.agentruntime.domain.ChatRequest;
import cn.project.base.agentruntime.rag.DocumentService;
import cn.project.base.agentruntime.rag.RagService;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI Agent REST API —— 对话接口与知识库管理接口。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final RagService ragService;
    private final DocumentService documentService;

    public AgentController(AgentService agentService, RagService ragService, DocumentService documentService) {
        this.agentService = agentService;
        this.ragService = ragService;
        this.documentService = documentService;
    }

    // ==================== 对话接口 ====================

    /**
     * 同步对话
     */
    @PostMapping("/chat")
    public AgentResult chat(@RequestBody ChatRequest request) {
        return agentService.chat(request);
    }

    /**
     * 流式对话（SSE）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return agentService.chatStream(request);
    }

    // ==================== 知识库管理 ====================

    /**
     * 重新导入文档到知识库
     */
    @PostMapping("/knowledge/import")
    public String importDocuments() {
        documentService.importDocuments();
        return "Documents imported successfully";
    }

    /**
     * 直接添加文本到知识库
     */
    @PostMapping("/knowledge/add")
    public String addKnowledge(@RequestBody Map<String, String> body) {
        ragService.storeDocument(body.get("content"), Map.of(
                "name", body.getOrDefault("name", "manual"),
                "source", "api"));
        return "Document stored";
    }

    /**
     * 检索知识库
     */
    @PostMapping("/knowledge/search")
    public List<Document> searchKnowledge(@RequestBody Map<String, String> body) {
        int topK = Integer.parseInt(body.getOrDefault("topK", "5"));
        return ragService.search(body.get("query"), topK);
    }

    // ==================== 健康检查 ====================

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "spring-ai-agent-runtime");
    }
}
