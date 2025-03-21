package cn.project.base.ragservice.service;

import com.sun.tools.javac.Main;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByLineSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tech.amikos.chromadb.Client;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class InitService {
    private static final String CHROMA_DB_DEFAULT_COLLECTION_NAME = "java-langChain-database-demo";
    private static final String CHROMA_URL = "http://localhost:8000";

    public static void main(String[] args) {
        //======================= 1.加载文件=======================
        URL docUrl = Main.class.getClassLoader().getResource("笑话.txt");
        Document document = getDocument(docUrl);
        //======================= 2.拆分文件内容=======================
        //参数：分段大小（一个分段中最大包含多少个token）、重叠度（段与段之前重叠的token数）、分词器（将一段文本进行分词，得到token）
        //langchain4j 6种分词方式：
        // 1.基于字符的：逐个字符（含空白字符）分割DocumentByCharacterSplitter
        // 2.基于行的：按照换行符（\n）分割DocumentByLineSplitter
        // 3.基于段落的：按照连续的两个换行符（\n\n）分割；DocumentByParagraphSplitter
        // 4.基于正则的：按照自定义正则表达式分隔 DocumentByRegexSplitter
        // 5.基于句子的（使用Apache OpenNLP，只支持英文，所以可以忽略）DocumentBySentenceSplitter
        // 6.基于字的：将文本按照空白字符分割 DocumentByWordSplitter
        DocumentByLineSplitter lineSplitter = new DocumentByLineSplitter(200, 0, new OpenAiTokenizer());
        //DocumentSplitter splitter = DocumentSplitters.recursive(150,10,new OpenAiTokenizer());
        List<TextSegment> segments = lineSplitter.split(document);
        log.info("segment的数量是: {}", segments.size());
        //查看分段后的信息
        segments.forEach(segment -> log.info("========================segment: {}", segment.text()));
        //文本向量化 并存储到向量数据库
        //======================= 3.文本向量化=======================
        OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();

        //======================= 4.向量库存储=======================
        Client client = new Client(CHROMA_URL);

        //创建向量数据库
        EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore.builder()
                .baseUrl(CHROMA_URL)
                .collectionName(CHROMA_DB_DEFAULT_COLLECTION_NAME)
                .build();

        segments.forEach(segment -> {
            Embedding e = embeddingModel.embed(segment).content();
            embeddingStore.add(e, segment);
        });
        //======================= 5.向量库检索=======================
        String qryText = "北极熊";
        Embedding queryEmbedding = embeddingModel.embed(qryText).content();
//查询文本嵌入向量(queryEmbedding）、最大查询数量（最多查询多少个距离最近的向量）、最小分值（通过该值过滤一些候选值）、元数据过滤器（根据元数据过滤一些候选值）
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).maxResults(1).build();
        EmbeddingSearchResult<TextSegment> embeddedEmbeddingSearchResult = embeddingStore.search(embeddingSearchRequest);
        List<EmbeddingMatch<TextSegment>> embeddingMatcheList = embeddedEmbeddingSearchResult.matches();
        EmbeddingMatch<TextSegment> embeddingMatch = embeddingMatcheList.get(0);
        TextSegment textSegment = embeddingMatch.embedded();
        log.info("查询结果: {}", textSegment.text());
        //======================= 6.与LLM交互=======================
        PromptTemplate promptTemplate = PromptTemplate.from("基于如下信息用中文回答:\n" +
                "{{context}}\n" +
                "提问:\n" +
                "{{question}}");
        Map<String, Object> variables = new HashMap<>();
        //以向量库检索到的结果作为LLM的信息输入
        variables.put("context", textSegment.text());
        variables.put("question", "北极熊干了什么");
        Prompt prompt = promptTemplate.apply(variables);

        //连接大模型
        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();

        UserMessage userMessage = prompt.toUserMessage();
        Response<AiMessage> aiMessageResponse = ollamaChatModel.generate(userMessage);
        AiMessage response = aiMessageResponse.content();
        log.info("大模型回答: {}", response.text());
    }


//    private static Document getDocument(String fileName) {
//        URL docUrl = LangChainMainTest.class.getClassLoader().getResource(fileName);
//        if (docUrl == null) {
//            log.error("未获取到文件");
//        }
//
//        Document document = null;
//        try {
//            Path path = Paths.get(docUrl.toURI());
//            document = FileSystemDocumentLoader.loadDocument(path);
//        } catch (URISyntaxException e) {
//            log.error("加载文件发生异常", e);
//        }
//        return document;
//    }


    private void vector() {
        URL docUrl = Main.class.getClassLoader().getResource("笑话.txt");
        if (docUrl == null) {
            log.error("未获取到文件");
        }
        Document document = getDocument(docUrl);
        if (document == null) {
            log.error("加载文件失败");
        }

    }

    private static Document getDocument(URL resource) {
        Document document = null;
        try {
            Path path = Paths.get(resource.toURI());
            document = FileSystemDocumentLoader.loadDocument(path);
        } catch (URISyntaxException e) {
            log.error("加载文件发生异常", e);
        }
        return document;
    }

}
