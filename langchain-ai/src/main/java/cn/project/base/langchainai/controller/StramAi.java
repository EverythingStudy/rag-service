package cn.project.base.langchainai.controller;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.concurrent.TimeUnit;

public class StramAi {
    public static void main(String[] args) {

        StreamingChatLanguageModel model = OpenAiStreamingChatModel.withApiKey("demo");


        model.generate("你好，你是谁？", new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {

                System.out.println(token);

                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            public void onError(Throwable error) {
                System.out.println(error);
            }
        });

    }
}
