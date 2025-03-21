package cn.project.base.springaimodel.domain;


import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;

import java.util.List;
import java.util.Map;

public class Template {
    public static Prompt pormpt() {
        PromptTemplate promptTemplate = new PromptTemplate("请给我讲一个关于{topic}主题的故事");
        Message userMessage = promptTemplate.createMessage(Map.of("topic", "西游记"));
        String systemText = "你是一个擅长讲中国古典故事的高手，请你用 {voice} 的语言风格回复用户的请求。";
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("voice", "幽默"));
        Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
        return prompt;
    }
}
