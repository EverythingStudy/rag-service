package cn.project.base.ragmiluvs.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

@Service
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
    @Resource
    private StreamingChatLanguageModel streamingChatLanguageModel;
    @Resource
    private ChatLanguageModel chatLanguageModel;
    @Resource
    private EmbeddingModel embeddingModel;
    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;
    @Resource
    private ChatMemoryStore chatmemoryStore;

    /**
     * RAG主要实现逻辑
     *
     * @param chatId  对话ID
     * @param message 用户提问的内容
     * @return 返回检索增强生成的内容
     */
    public Flux<String> chatStream(String chatId, String message) {

        AiServices<Customer> aiServices = AiServices.builder(Customer.class)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(chatId)
                        .chatMemoryStore(chatmemoryStore)
                        .maxMessages(50)
                        .build());

        aiServices.streamingChatLanguageModel(streamingChatLanguageModel);
        aiServices.chatLanguageModel(chatLanguageModel);

        // 先进行知识库检索
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .maxResults(10)
                .minScore(0.5)
                .build();

        //将用户的查询和前面的对话压缩到一个独立的查询中。可以显著提高检索质量。
        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatLanguageModel);

        //检索增强生成
        aiServices.retrievalAugmentor(DefaultRetrievalAugmentor
                .builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .build());

        Customer customer = aiServices.build();
        return customer.stream(chatId, message);
    }

    /**
     * 解析文档、切片、embedding、并保存到向量数据库
     */
    public void importDocuments() {
        try {
            log.info("===================开始导入文档到向量数据库");
            org.springframework.core.io.Resource[] resourceList = resourcePatternResolver.getResources("classpath:documents/*");
            for (org.springframework.core.io.Resource resource : resourceList) {
                File file = resource.getFile();
                log.info("导入文档的名称为：" + file.getName());
                Document document = FileSystemDocumentLoader.loadDocument(file.getAbsolutePath(), new ApacheTikaDocumentParser());
                document.metadata().put("docsName", file.getName());

                DocumentSplitter splitter = new DocumentByParagraphSplitter(1000, 0);
                List<TextSegment> segments = splitter.split(document);
                log.info("对文档的切片数量为：" + segments.size());
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                embeddingStore.addAll(embeddings, segments);
                log.info("===================导入文档到向量数据库完成");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
