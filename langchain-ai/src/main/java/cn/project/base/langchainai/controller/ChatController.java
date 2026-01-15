package cn.project.base.langchainai.controller;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    ChatModel chatLanguageModel;

    ChatController(ChatModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    @GetMapping("/hello")
    public String hello(@RequestParam(value = "message", defaultValue = "请给我讲一个笑话") String message) {
        return chatLanguageModel.chat(message);
    }

    public static void main(String[] args) {
        ChatModel model = QwenChatModel.builder()
                .apiKey("sk-7d8637e8c1b34e7aa901dade275a1c7b")
                .modelName("qwen-plus")
                //以下的参数，根据具体业务需求设置
                .temperature(0.5F)
                .topK(2)
                .topP(0.7)
                .build();
        String answer = model.chat("请给我讲一个笑话");
        System.out.println(answer);
    }
}
