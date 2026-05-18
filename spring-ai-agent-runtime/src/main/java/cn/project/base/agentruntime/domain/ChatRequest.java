package cn.project.base.agentruntime.domain;

public class ChatRequest {
    private String message;
    private String conversationId;
    private boolean useRag;

    public ChatRequest() {}

    public ChatRequest(String message, String conversationId, boolean useRag) {
        this.message = message;
        this.conversationId = conversationId;
        this.useRag = useRag;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public boolean isUseRag() { return useRag; }
    public void setUseRag(boolean useRag) { this.useRag = useRag; }
}
