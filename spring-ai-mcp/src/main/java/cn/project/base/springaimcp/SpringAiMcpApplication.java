package cn.project.base.springaimcp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringAiMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiMcpApplication.class, args);
    }
    @Bean
    public CommandLineRunner predefinedQuestions(ChatClient.Builder chatClientBuilder,
                                                 ToolCallbackProvider tools,
                                                 ConfigurableApplicationContext context) {
        return args -> {
            // 构建ChatClient并注入MCP工具
            var chatClient = chatClientBuilder
                    .defaultTools(tools)
                    .build();

            // 使用ChatClient与LLM交互
            String userInput = "北京的天气如何？";
            System.out.println("\n>>> QUESTION: " + userInput);
            System.out.println("\n>>> ASSISTANT: " + chatClient.prompt(userInput).call().content());

            context.close();
        };
    }

}
