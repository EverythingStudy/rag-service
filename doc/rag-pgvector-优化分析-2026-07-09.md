# RAG-PgVector 模块优化分析

**日期**: 2026-07-09

---

## Q: 分析 rag-pgvector 模块，这个模块是关于 rag 的一个服务。我需要你告诉我 rag 服务需要做哪些优化，本服务实现了哪几方面的优化。

## A: 详细的优化分析如下

### RAG 服务通用优化维度

一个完整的 RAG 服务通常需要在以下 6 个维度做优化：

1. **文档解析** — 多格式支持、流式解析、大文件分片
2. **文本切分** — 语义切分 vs 固定长度、重叠窗口、主题漂移检测
3. **向量检索** — 索引类型（HNSW/IVFFlat）、混合检索（向量+关键词）、重排序
4. **查询增强** — 查询重写、HyDE、多轮对话上下文
5. **生成优化** — 指令注入、上下文压缩、流式输出、引用溯源
6. **系统架构** — 并行处理、限流、缓存、连接池、异步批写入

---

### 本服务（`rag-pgvector`）已实现的优化

逐项对照，共 **17 项**：

#### 一、文档解析优化（3 项）

| 优化 | 实现位置 | 说明 |
|---|---|---|
| **多格式支持** | `DocumentIngestionService` | TXT/MD/JSON/YAML/CSV/HTML/XML + PDF(PDFBox) + DOCX(POI) + XLSX |
| **SAX 流式解析 Excel** | `extractTextFromXlsx()` | 使用 `XSSFReader` + `XSSFSheetXMLHandler`，不加载整个 workbook 到内存，避免 OOM |
| **流式读取 PDF** | `extractTextFromPdf()` | 用 `RandomAccessReadBuffer` 从 InputStream 流式读取，非一次性加载到内存 |

#### 二、文本切分优化（4 项）

| 优化 | 实现位置 | 说明 |
|---|---|---|
| **主题漂移检测** | `chunkDocument()` | 计算每个段落的 embedding 与窗口中心向量的余弦相似度，低于 0.7 则切分，语义更加连贯 |
| **增量式窗口中心** | `runningAverage()` | O(1) 更新当前窗口中心向量，不重新计算所有已累加向量 |
| **批量 Embedding** | `computeEmbeddings()` | 一个文件的所有段落一次性调用 DashScope embedding API，减少网络开销 |
| **智能重叠** | `chunkDocument()` | 切分时携带前一个 chunk 末尾 50 个字符，保持上下文连贯 |

#### 三、向量检索优化（3 项）

| 优化 | 实现位置 | 说明 |
|---|---|---|
| **HNSW 索引** | `application.yml` | 使用 HNSW（Hierarchical Navigable Small World）近似最近邻索引，比 IVFFlat 召回率更高、查询更快 |
| **过采样 + 重排序** | `QueryService.rerank()` | 检索 `topK × 3` 个候选，重排序后取 topK，减少边缘漏召 |
| **混合评分** | `computeHybridScore()` | 60% 向量相似度 + 40% 关键词重叠（轻量 BM25），兼顾语义和词法匹配 |

#### 四、查询增强优化（1 项）

| 优化 | 实现位置 | 说明 |
|---|---|---|
| **LLM 查询重写** | `rewriteQuery()` | 用 DeepSeek 将用户自然语言重写为核心关键词，提升向量检索 recall；失败时降级为原查询 |

#### 五、生成优化（3 项）

| 优化 | 实现位置 | 说明 |
|---|---|---|
| **上下文压缩** | `buildContext()` | 限制最大 8000 字符，超长时截断（至少保留第一篇文档） |
| **流式输出（SSE）** | `QueryController.streamQuery()` | 通过 `Flux<String>` 实现 token 级别流式返回，显著降低首 Token 延迟（TTFT） |
| **引用溯源** | `QueryResponse.Reference` | 每个引用附带来源文件名、混合评分、截断内容（200 字），增强可信度 |

#### 六、系统架构优化（3 项）

| 优化 | 实现位置 | 说明 |
|---|---|---|
| **并行文件处理** | `processFiles()` | `CompletableFuture` + 自定义线程池（默认 4 线程，最大 8） |
| **信号量限流** | `processSingleFile()` | 默认最多同时处理 2 个文件，避免打爆 embedding API 和数据库 |
| **分批写入向量库** | `processSingleFile()` | 每 50 个 chunk 调一次 `vectorStore.add()`，避免大事务和内存堆积 |
| **优雅关闭** | `RagPgConfig` | 线程池设置 `waitForTasksToCompleteOnShutdown` + 30s 超时，确保任务不丢失 |

---

### 架构总览

```
                      +------------------+
                      |  Client (REST)   |
                      +--------+---------+
                               |
               +---------------+---------------+
               |                               |
    +----------v----------+       +------------v-----------+
    | DocumentController  |       |   QueryController      |
    | /rag/document/*     |       |   /rag/query/*         |
    +----------+----------+       +------------+-----------+
               |                               |
    +----------v----------+       +------------v-----------+
    |DocumentIngestionSvc |       |     QueryService        |
    |  - extractText()    |       |  1. rewriteQuery()      |
    |  - chunkDocument()  |       |  2. searchSimilar()     |
    |  - computeEmbeddings|       |  3. rerank()            |
    |  - processFiles()   |       |  4. buildContext()      |
    +----------+----------+       |  5. chatClient.call()   |
               |                  +------------+-----------+
               |                               |
    +----------v----------+       +------------v-----------+
    |   EmbeddingModel    |       |      ChatClient         |
    | (DashScope text-    |       | (DeepSeek via OpenAI-   |
    |  embedding-v2,1536d)|       |  compatible API, v4)    |
    +---------------------+       +-------------------------+
               |                               |
    +----------v-------------------------------v-----------+
    |              PgVectorStore (Spring AI)               |
    |   PostgreSQL + pgvector extension + HNSW index       |
    |   Table: vector_store (auto-created)                 |
    +------------------------------------------------------+
```

### 未覆盖的优化（可进一步改进）

- **缓存**：未使用任何结果缓存（Caffeine/Redis），重复查询仍需走完整链路
- **连接池调优**：HikariCP 使用默认值，未显式配置 `max-pool-size` 等
- **多路召回**：仅用向量检索，未接入 BM25/Tantivy 等全文检索引擎作为互补
- **HyDE**：未在检索前生成假设性文档
- **多轮对话**：`chatId` 字段已预留但未实际使用，暂无法基于历史对话做上下文检索

---

### 关键配置

| 配置项 | 值 | 说明 |
|---|---|---|
| `spring.ai.vectorstore.pgvector.index-type` | HNSW | 高效近似最近邻索引 |
| `spring.ai.vectorstore.pgvector.distance-type` | COSINE_DISTANCE | 余弦距离 |
| `spring.ai.vectorstore.pgvector.dimension` | 1536 | text-embedding-v2 维度 |
| `rag.ingestion.chunk.max-size` | 500 | 每块最大字符数 |
| `rag.ingestion.chunk.overlap` | 50 | 块间重叠字符数 |
| `rag.ingestion.chunk.topic-threshold` | 0.7 | 主题漂移阈值 |
| `rag.ingestion.batch.size` | 50 | 每批写入向量库的文档数 |
| `rag.ingestion.threads` | 4 | 文档处理线程池大小 |
| `rag.query.rewrite.enabled` | true | 启用 LLM 查询重写 |
| `rag.query.rerank.multiplier` | 3 | 过采样倍率 |
| `rag.query.rerank.vector-weight` | 0.6 | 混合评分向量权重 |
| `rag.query.context.max-chars` | 8000 | 最大上下文字符数 |
