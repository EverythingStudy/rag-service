package cn.project.base.langchainai.controller;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

public class StramAi {
    public static void main(String[] args) {

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder().apiKey("demo").build();


        model.chat("你好，你是谁？", new StreamingChatResponseHandler() {
            //            @Override
//            public void onNext(String token) {
//
//                System.out.println(token);
//
//                try {
//                    TimeUnit.SECONDS.sleep(1);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//
//            }
            //PartialThinking thinking = new PartialThinking("");


            @Override
            public void onPartialResponse(String token) {
                StreamingChatResponseHandler.super.onPartialResponse(token);
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {

            }

            @Override
            public void onError(Throwable error) {
                System.out.println(error);
            }
        });

    }
}
