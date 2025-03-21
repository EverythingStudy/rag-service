package cn.project.base.springaimodel.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

@RestController
@RequestMapping("/ai")
public class ImageController {
    private final ImageModel imageModel;
    private static final String DEFAULT_PROMPT = "为人工智能生成一张富有科技感的图片！";

    public ImageController(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    /**
     * 1）Spring AI文生图接口使用
     *
     * Spring AI为文生图功能设计了直观易用的API接口，使得开发者可以通过简单的调用来生成图像。
     * 通常情况下，这类接口会接受一个或多个文本提示作为输入，并返回生成的图像数据或者图像文件的URL链接。
     * 2）ImageModel 类的使用
     *
     * ImageModel 类是Spring AI框架中用于表示图像生成模型的核心组件之一。它的主要职责包括但不限于：
     *
     * 封装模型信息：存储有关特定图像生成模型的关键元数据，例如模型名称、版本号、支持的输入格式等。
     * 管理模型加载：负责初始化并加载预训练的图像生成模型到内存中，以便后续可以高效地进行推理操作。
     * 定义推理逻辑：提供标准化的方法来执行基于文本提示的图像生成任务，确保不同模型之间的行为一致性。
     * 处理输出结果：将模型生成的原始图像数据转换为易于使用的格式，如JPEG、PNG等，并可能附加额外的元信息（如生成时间戳、所用模型ID等）。
     *
     * @param response
     */
    @GetMapping("/image")
    public void image(HttpServletResponse response) {

        ImageResponse imageResponse = imageModel.call(new ImagePrompt(DEFAULT_PROMPT));
        String imageUrl = imageResponse.getResult().getOutput().getUrl();

        try {
            URL url = URI.create(imageUrl).toURL();
            InputStream in = url.openStream();

            response.setHeader("Content-Type", MediaType.IMAGE_PNG_VALUE);
            response.getOutputStream().write(in.readAllBytes());
            response.getOutputStream().flush();
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
