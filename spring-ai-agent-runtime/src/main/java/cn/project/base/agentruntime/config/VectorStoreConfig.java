package cn.project.base.agentruntime.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置 —— 当没有外部向量存储（Chroma / Milvus）时，
 * 使用 SimpleVectorStore（内存实现，重启后数据丢失）。
 * <p>
 * 生产环境可替换为：
 * <ul>
 *   <li>ChromaEmbeddingStore (spring-ai-chroma-store-spring-boot-starter)</li>
 *   <li>MilvusVectorStore (spring-ai-milvus-store-spring-boot-starter)</li>
 *   <li>PgVectorStore (spring-ai-pgvector-store-spring-boot-starter)</li>
 * </ul>
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
