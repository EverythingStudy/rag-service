package cn.project.base.springaimodel.controller;


import com.alibaba.cloud.ai.dashscope.audio.DashScopeSpeechSynthesisOptions;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisModel;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisPrompt;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisResponse;
import jakarta.annotation.PreDestroy;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * 在阿里巴巴的Spring AI Alibaba开源框架中，TTS（Text-to-Speech，文本转语音）功能允许将文本转换为自然流畅的语音输出。这一特性对于构建智能客服、语音助手、导航系统等需要语音交互的应用程序非常重要。
 *
 * TTS技术原理：TTS使用先进的语音合成模型，这些模型基于深度学习算法训练而成，可以生成高质量、接近真人发音的语音。
 * TTS应用场景：适用于各种需要语音输出的场景，如智能家居设备、移动应用中的语音提示、在线教育平台的朗读功能等。
 */
@RestController
@RequestMapping("/ai/tts")
public class TTSController implements ApplicationRunner {

    private final SpeechSynthesisModel speechSynthesisModel;

    private static final String TEXT = "床前明月光， 疑是地上霜。 举头望明月， 低头思故乡。";

    private static final String FILE_PATH = "src/main/resources/tts";

    public TTSController(SpeechSynthesisModel speechSynthesisModel) {
        this.speechSynthesisModel = speechSynthesisModel;
    }

    @GetMapping("/simple")
    public void tts() throws IOException {

        // 使用构建器模式创建 DashScopeSpeechSynthesisOptions 实例并设置参数
        DashScopeSpeechSynthesisOptions options = DashScopeSpeechSynthesisOptions.builder()
                .withSpeed(1.0)        // 设置语速
                .withPitch(0.9)         // 设置音调
                .withVolume(60)         // 设置音量
                .build();

        SpeechSynthesisResponse response = speechSynthesisModel.call(
                new SpeechSynthesisPrompt(TEXT,options)
        );

        File file = new File(FILE_PATH + "/output.mp3");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ByteBuffer byteBuffer = response.getResult().getOutput().getAudio();
            fos.write(byteBuffer.array());
        }
        catch (IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    @GetMapping("/stream")
    public void streamTTS() {

        Flux<SpeechSynthesisResponse> response = speechSynthesisModel.stream(
                new SpeechSynthesisPrompt(TEXT)
        );

        CountDownLatch latch = new CountDownLatch(1);
        File file = new File(FILE_PATH + "/output-stream.mp3");
        try (FileOutputStream fos = new FileOutputStream(file)) {

            response.doFinally(
                    signal -> latch.countDown()
            ).subscribe(synthesisResponse -> {
                ByteBuffer byteBuffer = synthesisResponse.getResult().getOutput().getAudio();
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                try {
                    fos.write(bytes);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            latch.await();
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run(ApplicationArguments args) {

        File file = new File(FILE_PATH);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    @PreDestroy
    public void destroy() throws IOException {

        FileUtils.deleteDirectory(new File(FILE_PATH));
    }

}
