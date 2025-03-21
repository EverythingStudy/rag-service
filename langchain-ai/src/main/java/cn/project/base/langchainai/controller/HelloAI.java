package cn.project.base.langchainai.controller;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
@RequestMapping(value = "/ai")
public class HelloAI {
    @Autowired
    private ChatLanguageModel  model;
    @GetMapping("/chat")
    public String ai(){
        return model.chat("你好啊");
    }
    public static void main(String[] args) {

        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .build();

/**
 * ChatMessage是一个接口，表示聊天消息，它有以下四种实现：
 *
 * UserMessage：表示用户发送给大模型的消息
 * AiMessage：表示大模型响应给用户的消息
 * SystemMessage：也是用户发送给大模型的消息，和UserMessage不同在于，SystemMessage一般是应用程序帮用户设置的，举个例子，假如有一个作家应用，那么“请你扮演一名作家，请帮我写一篇关于春天的作文”，其中“请你扮演一名作家”就是SystemMessage，“请帮我写一篇关于春天的作文”就是UserMessage
 * ToolExecutionResultMessage：也是用户发送给大模型的，表示工具的执行结果，关于LangChain4j的工具机制，会在后续介绍，目前可以忽略

 */
        UserMessage userMessage1 = UserMessage.userMessage("你好，我是小齐");
        Response<AiMessage> response1 = model.generate(userMessage1);
        AiMessage aiMessage1 = response1.content(); // 大模型的第一次响应
        System.out.println(aiMessage1.text());
        System.out.println("----");

        // 下面一行代码是重点
        Response<AiMessage> response2 = model.generate(List.of(userMessage1, aiMessage1, UserMessage.userMessage("我叫什么")));
        AiMessage aiMessage2 = response2.content(); // 大模型的第二次响应
        System.out.println(aiMessage2.text());

    }
}
