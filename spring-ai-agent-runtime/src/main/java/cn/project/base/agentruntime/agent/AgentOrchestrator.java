package cn.project.base.agentruntime.agent;

import cn.project.base.agentruntime.domain.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * 核心 Agent 编排器 —— 协调 LLM、MCP 工具、内置技能和 RAG 检索。
 * <p>
 * ChatClient 已在 AgentConfig 中预配置了 defaultTools（MCP + 本地技能）
 * 和 defaultAdvisors（RAG + 记忆），此处直接使用即可。
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final ChatClient chatClient;

    public AgentOrchestrator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 同步对话
     */
    public AgentResult chat(String message, String conversationId) {
        String convId = resolveConversationId(conversationId);

        ChatResponse response = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, convId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 20))
                .call()
                .chatResponse();

        String content = response.getResult().getOutput().getText();
        log.debug("[{}] Agent: {}", convId, content);

        return new AgentResult(content, convId);
    }

    /**
     * 流式对话（SSE）
     */
    public Flux<String> chatStream(String message, String conversationId) {
        String convId = resolveConversationId(conversationId);

        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, convId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 20))
                .stream()
                .content()
                .doOnComplete(() -> log.debug("[{}] Stream complete", convId));
    }

    private static String resolveConversationId(String convId) {
        return convId != null && !convId.isBlank() ? convId : UUID.randomUUID().toString();
    }
}
