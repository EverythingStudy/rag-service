package cn.project.base.agentruntime.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置 —— PGVector 为主，SimpleVectorStore 为兜底。
 * <p>
 * 当 spring-ai-pgvector-store-spring-boot-starter 在类路径上时，
 * PgVectorStore 自动配置生效，该兜底不生效。
 */
// @Configuration
// public class VectorStoreConfig {

//     @Bean
//     @ConditionalOnMissingBean(VectorStore.class)
//     public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
//         return SimpleVectorStore.builder(embeddingModel).build();
//     }
// }
