package cn.project.base.langchainai.controller;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.openai.OpenAiImageModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.output.Response;

public class AIModeration {
    //ModerationModel能够校验输入中是否存在敏感内容。
    public static void main(String[] args) {
        ModerationModel moderationModel = OpenAiModerationModel.builder().apiKey("demo").build();
        Response<Moderation> response = moderationModel.moderate("你好");
        System.out.println(response.content().flaggedText());

    }

    public void test() {
        ImageModel imageModel = OpenAiImageModel.builder()
                .baseUrl("http://localhost:3000/v1")
                .apiKey("sk-xxxxxxx")
                .build();
        Response<Image> response = imageModel.generate("一只兔子");
        System.out.println(response.content().url());
    }
}
