package cn.project.base.agentruntime.skill.builtin;

import cn.project.base.agentruntime.rag.RagService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库搜索技能 —— Agent 在需要时主动调用此工具检索 RAG 知识库。
 * <p>
 * 相比 Spring AI 的 QuestionAnswerAdvisor 自动嵌入，
 * 此方式将检索控制权交给 Agent，避免 embedding 服务不可用时阻塞对话。
 */
@Service
public class KnowledgeSearchSkill {

    private final RagService ragService;

    public KnowledgeSearchSkill(RagService ragService) {
        this.ragService = ragService;
    }

    @Tool(description = "从知识库中搜索与问题相关的信息，用于 RAG 增强检索")
    public String searchKnowledge(
            @ToolParam(description = "搜索查询，越具体越好") String query,
            @ToolParam(description = "返回结果数量，默认 3") int topK) {

        if (topK <= 0) topK = 3;

        try {
            List<Document> results = ragService.search(query, topK);
            if (results.isEmpty()) {
                return "知识库中未找到相关信息";
            }

            return results.stream()
                    .map(doc -> String.format("[相关度 %.2f] %s",
                            doc.getMetadata().getOrDefault("distance", 0),
                            doc.getText()))
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            return "知识库检索失败: " + e.getMessage();
        }
    }
}
