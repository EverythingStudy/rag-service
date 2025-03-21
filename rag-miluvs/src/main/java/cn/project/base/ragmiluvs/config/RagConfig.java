package cn.project.base.ragmiluvs.config;

import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {
    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    @Value("${milvus.host}")
    private String host;
    @Value("${milvus.port}")
    private Integer port;

    @Bean
    public ChatMemoryStore chatMemoryStore() {
        log.info("==========初始化ChatMemoryStore");
        return new InMemoryChatMemoryStore();
    }

    @Bean
    public EmbeddingStore createEmbeddingStore() {

        log.info("==========开始创建Milvus的Collection");
        MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName("langchain_01")
                .dimension(1536)
                .indexType(IndexType.FLAT)
                .metricType(MetricType.COSINE)
//                .username("username")
//                .password("password")
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .autoFlushOnInsert(true)
                .idFieldName("id")
                .textFieldName("text")
                .metadataFieldName("metadata")
                .vectorFieldName("vector")
                .build();
        log.info("==========创建Milvus的Collection完成");
        return store;
    }
}
