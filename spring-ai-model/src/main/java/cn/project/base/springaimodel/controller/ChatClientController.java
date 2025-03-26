package cn.project.base.springaimodel.controller;

import cn.project.base.springaimodel.domain.Template;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@RestController
@RequestMapping("/ai")
@Slf4j
public class ChatClientController {
    Logger logger = LoggerFactory.getLogger(ChatClientController.class);

    private final ChatClient chatClient;

    private final ChatModel chatModel;

    public ChatClientController(ChatModel chatModel) {

        this.chatModel = chatModel;

        // 构造时，可以设置 ChatClient 的参数
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .withTopP(0.7)
                                .build()
                )
                .build();
    }

    /**
     * ChatClient 简单调用
     */
    @GetMapping("/simple/chat")
    public String simpleChat(String input) {
        return chatClient.prompt().user(input).call().content();
    }

    /**
     * ChatClient 流式调用
     */
    @GetMapping("/stream/chat")
    public Flux<String> streamChat(HttpServletResponse response, String input) {

        response.setCharacterEncoding("UTF-8");
        return chatClient.prompt().user(input).stream().content();
    }

    /**
     * 在Spring AI框架中，Prompt类用于构建和管理与大型语言模型（LLM）交互时所需的提示词（prompt）。通过创建一个Prompt对象，并向其中添加用户消息（userMessage）和系统消息（systemMessage），你可以定义与LLM对话的上下文和规则。具体来说，Prompt prompt = new Prompt(List.of(userMessage, systemMessage));这种提示词模板的用法有如下好处：
     * <p>
     * 1）多轮对话支持
     * <p>
     * List.of(userMessage, systemMessage)构建了一个包含多个消息项的不可变列表。每个消息项代表对话中的一个回合，可以是用户输入或系统响应。
     * 通过这种方式，开发者可以在一次请求中传递多条历史对话记录给LLM，帮助其更好地理解当前对话的背景信息，从而生成更连贯、更准确的回答。
     * 2）角色区分
     * <p>
     * 在多轮对话中，明确区分哪些是用户发出的消息（userMessage），哪些是由系统（如AI助手）产生的消息（systemMessage），有助于LLM正确地模拟对话角色，提供更加自然的交互体验。
     * 这种做法对于实现复杂的对话逻辑特别有用，比如客服机器人、虚拟助手等应用场景。
     * 3）上下文设定
     * <p>
     * systemMessage可以用来设置对话的整体语境或规则，例如指定回答风格、领域知识范围等。这对于确保LLM生成的内容符合特定要求非常重要。
     * 例如，如果希望LLM以专业且礼貌的方式回应，则可以在systemMessage中加入相应的指示。
     * 4）动态调整
     * <p>
     * 使用List.of()构造函数使得每次构建Prompt对象时都可以灵活地传入不同的消息组合，便于根据实际需求动态调整对话内容。
     * 开发者可以根据用户的实时反馈或其他条件变化，适时更新对话上下文，使LLM能够适应不断变化的对话场景。
     *
     * @param response
     * @param input
     * @return
     */
    @GetMapping("/stream/pormpt/chat")
    public AssistantMessage streamPormptChat(HttpServletResponse response, String input) {

        response.setCharacterEncoding("UTF-8");
        return chatClient.prompt(Template.pormpt()).call().chatResponse().getResult().getOutput();
    }

    /**
     * 消息cache 基于内存得cache
     * 开发者也可以自行实现ChatMemory基于类似于文件、Redis等方式进行上下文内容的存储和记录。
     *
     * @return
     */
    @GetMapping("/cache/pormpt/chat")
    public String cachePromptChat() {
        //初始化基于内存的对话记忆
        ChatMemory chatMemory = new InMemoryChatMemory();
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();

        String conversantId = UUID.randomUUID().toString();
        ChatResponse response = chatClient
                .prompt()
                .user("我想去新疆")
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversantId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getContent();
        Assert.notNull(content, "");

        logger.info("content: {}", content);

        response = chatClient
                .prompt()
                .user("可以帮我推荐一些美食吗")
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversantId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        content = response.getResult().getOutput().getContent();
        Assert.notNull(content, "");
        logger.info("content: {}", content);
        return content;
    }

}
