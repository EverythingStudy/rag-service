package cn.project.base.agentruntime.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgentConfig {

    @Value("${agent.max-retrieval-results:5}")
    private int maxRetrievalResults;

    @Value("${agent.retrieval-min-score:0.5}")
    private double retrievalMinScore;

    @Value("${agent.system-prompt}")
    private String systemPrompt;

    /**
     * 会话记忆存储（内存实现，可替换为 Redis 等持久化方案）
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    /**
     * 构建核心 ChatClient，注入 MCP 工具、内置技能、记忆 Advisor
     * <p>
     * 通过 List<ToolCallbackProvider> 收集所有工具提供者，包括：
     * <ul>
     *   <li>Spring AI MCP 自动配置注入的 MCP 工具（MySQL、Redis、PostgreSQL）</li>
     *   <li>SkillConfig 注册的内置技能（含知识库搜索工具）</li>
     * </ul>
     * RAG 增强检索通过 KnowledgeSearchSkill 以工具形式按需调用，
     * 避免 QuestionAnswerAdvisor 在 embedding 服务不可用时阻塞对话。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 List<ToolCallbackProvider> toolProviders,
                                 ChatMemory chatMemory) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultTools(toolProviders.toArray(new ToolCallbackProvider[0]))
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }
}
