package cn.project.base.agentruntime.domain;

import java.time.LocalDateTime;

public class AgentResult {
    private String content;
    private String conversationId;
    private LocalDateTime timestamp;
    private boolean toolCalled;
    private boolean ragUsed;

    public AgentResult() {}

    public AgentResult(String content, String conversationId) {
        this.content = content;
        this.conversationId = conversationId;
        this.timestamp = LocalDateTime.now();
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public boolean isToolCalled() { return toolCalled; }
    public void setToolCalled(boolean toolCalled) { this.toolCalled = toolCalled; }
    public boolean isRagUsed() { return ragUsed; }
    public void setRagUsed(boolean ragUsed) { this.ragUsed = ragUsed; }
}
