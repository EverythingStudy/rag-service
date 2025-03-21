package cn.project.base.springaiservice.controller;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("ollama/")
public class OllamaChatModelController {
    private final ChatModel ollamaChatModel;

    public OllamaChatModelController(ChatModel chatModel) {
        this.ollamaChatModel = chatModel;
    }

    @GetMapping("/simple/chat")
    public String simpleChat() {

        return ollamaChatModel.call(new Prompt("给我介绍一下狗")).getResult().getOutput().getText();
    }
}
