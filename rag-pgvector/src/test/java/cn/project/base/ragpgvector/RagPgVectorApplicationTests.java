package cn.project.base.ragpgvector;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
        PgVectorStoreAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@MockBean(VectorStore.class)
@MockBean(ChatModel.class)
@MockBean(EmbeddingModel.class)
class RagPgVectorApplicationTests {

    @Test
    void contextLoads() {
    }
}
