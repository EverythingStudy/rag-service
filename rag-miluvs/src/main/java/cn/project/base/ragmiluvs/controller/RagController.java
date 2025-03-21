package cn.project.base.ragmiluvs.controller;

import cn.project.base.ragmiluvs.service.RagService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/rag")
public class RagController {
    @Resource
    private RagService chatService;

    @GetMapping(value = "/dbinit")
    public String dbInit() {
        chatService.importDocuments();
        return "OK";
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam(value = "chatId", required = false, defaultValue = "1") String chatId, @RequestParam(value = "message") String message) {
        return chatService.chatStream(chatId, message);
    }
}
