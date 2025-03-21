package cn.project.base.ragmiluvs.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface Customer {
    @SystemMessage("""
            你是云程低代码平台客服人员，请你友好、礼貌、简洁回答客户的问题。
            你只回答与云程低代码平台相关问题，如果是其它问题，请礼貌拒绝。
            """)
    Flux<String> stream(@MemoryId String id, @UserMessage String message);
}
