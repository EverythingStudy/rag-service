package cn.project.base.function.controller;


import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class FunctionCallController {
    private static final Logger log = LoggerFactory.getLogger(FunctionCallController.class);

    @Autowired
    private ChatModel chatModel;

    @GetMapping(value = "/chat", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
    public String ragJsonText(@RequestParam(value = "userMessage") String userMessage) {
        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .system("""
                        	您是算术计算器的代理。
                        	您能够支持加法运算、乘法运算等操作，其余功能将在后续版本中添加，如果用户问的问题不支持请告知详情。
                            在提供加法运算、乘法运算等操作之前，您必须从用户处获取如下信息：两个数字，运算类型。
                            请调用自定义函数执行加法运算、乘法运算。
                            请讲中文。
                        """)
                .user(userMessage)
                .functions("addOperation", "mulOperation")//启用自定义函数
                .call()
                .content();
    }
    public static void main(String[] args) throws ClientProtocolException, IOException {
        // 设置 Ollama 的 API 地址（默认为 http://localhost:11434）
        String ollamaUrl = "http://localhost:11434/api/generate";

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost request = new HttpPost(ollamaUrl);

        // 设置请求头
        request.setHeader("Content-Type", "application/json");

        // 构建请求体（模型名称和输入内容）
        String jsonBody = "{ \"model\": \"deepseek-r1:14b\", \"prompt\": \"Hello, how are you?\" }";
        request.setEntity(new StringEntity(jsonBody));

        CloseableHttpResponse response = httpClient.execute(request);
        int statusCode = response.getStatusLine().getStatusCode();
        HttpEntity entityResponse = response.getEntity();
        String responseBody = EntityUtils.toString(entityResponse);

        System.out.println("Status Code: " + statusCode);
        System.out.println("Response Body: " + responseBody);

        // 关闭客户端
        httpClient.close();
    }
}
