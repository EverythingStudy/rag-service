package cn.project.base.agentruntime.agent;

import cn.project.base.agentruntime.domain.AgentResult;
import cn.project.base.agentruntime.domain.ChatRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Agent 服务层 —— 封装编排器，提供更高层级的业务接口。
 */
@Service
public class AgentService {

    private final AgentOrchestrator orchestrator;

    public AgentService(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public AgentResult chat(ChatRequest request) {
        return orchestrator.chat(request.getMessage(), request.getConversationId());
    }

    public Flux<String> chatStream(ChatRequest request) {
        return orchestrator.chatStream(request.getMessage(), request.getConversationId());
    }
}
