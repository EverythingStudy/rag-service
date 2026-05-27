package cn.project.base.ragpgvector.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class RagPgConfig {

    private static final Logger log = LoggerFactory.getLogger(RagPgConfig.class);

    @Value("${rag.ingestion.threads:4}")
    private int ingestionThreads;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(ingestionThreads);
        executor.setMaxPoolSize(ingestionThreads * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("rag-ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Ingestion executor initialized with {} threads", ingestionThreads);
        return executor;
    }
}
