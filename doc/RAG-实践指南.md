# RAG 学习与实践记录

> 基于 rag-pgvector 项目的完整实现笔记
> 从零开始搭建一个生产可用的 RAG 服务

---

## 前言

这篇文章记录了我在搭建 Spring AI + pgvector RAG 服务过程中的完整思考过程。从最基础的切片逻辑，到文件格式支持、大文件处理、查询优化，一步步踩过来。与其说是文档，不如说是把当时的困惑、尝试和理解写了下来，方便以后回顾。

---

## 第一章：RAG 到底在做什么

### 1.1 初次接触 RAG 的困惑

第一次看到 RAG（Retrieval-Augmented Generation）的架构图时，我最大的疑问是：

> **数据都在向量库里查到了，为什么还要过一遍 LLM？**

这个困惑很直接——如果向量数据库已经把相关段落找出来了，直接返回给用户不就行了吗？LLM 在这里面到底起了什么作用？

带着这个问题去读代码，才明白这两者的分工完全不同：

- **向量数据库**解决的是"找到相关材料"的问题
- **LLM**解决的是"用材料回答问题"的问题

用一个真实的场景来理解：

```
内部知识库里有三份文档:
  - doc1: "支付服务支持微信支付和支付宝"
  - doc2: "支付超时时间为 30 秒，超时后自动退款"
  - doc3: "退款需要联系客服并提供订单号"

用户问: "我付了钱但没到账怎么办？"

① 向量检索 → 找到 doc1（相似度 0.85）和 doc3（相似度 0.72）
② LLM 收到这两段材料后:
   - 理解用户遇到了"支付了但没到账"的异常
   - 从 doc3 知道"退款要联系客服"
   - 从 doc2 知道"超时会自动退款"
   - 整合成回答: "根据文档，超时未到账系统会自动退款（参考 doc2）；
     如果想主动退款，请联系客服并提供订单号（参考 doc3）"
```

如果只做检索不经过 LLM，用户得到的是两段原文，自己还得拼凑推理。经过 LLM 之后，变成了一个可以直接用的答案。

### 1.2 LLM 在 RAG 中具体做了什么

逐条拆开看，LLM 承担的职责其实很多：

**理解问题意图**
用户说"那个上传最大能多大"，LLM 知道这是在问文件大小限制。向量数据库只做向量匹配，不理解意图。

**提取关键信息**
从"系统支持 .txt .md .pdf .docx 格式"这个片段中，LLM 能提取出"系统支持的格式有四种"。向量数据库只输出整段原文。

**多片段整合**
文档 A 说了"支持的格式"，文档 B 说了"文件大小限制为 100MB"。用户问"我能上传 200MB 的 PDF 吗"，LLM 需要把这两个信息合在一起推理：支持 PDF，但超过 100MB，所以不能。

**否定判断**
查出来的 3 个段落和用户问的完全不相关，LLM 应该回答"知识库中未找到相关信息"，而不是强行瞎编。

**格式化输出**
LLM 可以把信息整理成对比表格、分点列表，而不是让用户看一堆原文。

**引用来源**
"根据[开发文档.md]第 3 节，系统支持... 但需要注意的是（参考[更新日志.md]）"——这种来源追溯，LLM 可以做到。

所以 RAG 的完整价值链是：

```
❌ 纯 LLM:         凭空回答 → 可能幻觉
❌ 纯检索:         返回一堆文档 → 用户自己翻
✅ RAG:            检索找到材料 + LLM 理解/整合/回答 → 准确且易读
```

---

## 第二章：切片，第一个遇上的实际问题

### 2.1 chunkDocument 初印象

项目里第一个接触的代码是 `DocumentIngestionService.chunkDocument()`。当时打开这个方法的实现，逻辑很清晰：

```
文本 → 按空行(\n\n)分段落 → 遍历段落累积 → 超过 500 字符切开 → 保留 50 字符 overlap
```

核心机制是：
- 用 `\n\s*\n` 切分段落
- 逐段追加到 `currentChunk`
- 如果追加后超过 `maxSize`（默认 500），就把当前 chunk 结算，保留末尾 `overlap`（默认 50 字符）作为下个 chunk 的开头
- 这样切分边界不会丢失上下文

当时觉得这个设计挺合理：段落是一个自然语义单元，在段落边界切分不会断开句子。

### 2.2 但 500 字符真的够吗

接着就发现了一个问题：

```java
if (currentChunk.length() + trimmed.length() + 1 > chunkMaxSize && !currentChunk.isEmpty()) {
```

这里检查的是"如果加上这段会超限"。但如果**单个段落本身就超过 500 字符**，判断条件中 `currentChunk.isEmpty()` 为 true（其实是段落刚刚放进去，还没触发结算），这段超长段落会被**整段塞进一个 chunk**，无视 max-size 限制。

这就是第一个矛盾：想保持段落完整性，但又控制不了段落长度。

更根本的问题是：**固定 500 字符 + 段落边界的组合，只能保证"不超过 500 字符的段落不被截断"，保证不了"chunk 内话题一致"。** 如果一篇文档里连着的两段话题完全不同，它们会被硬塞进同一个 chunk，污染检索质量。

### 2.3 更好的切分方法有哪些

知道了问题之后，开始调研其他方案：

**方案一：递归字符分割**
按优先级尝试多种分隔符：`段落(\n\n)` → `行(\n)` → `句子(。)` → `字符`。这样段落太长就降级到句子级截断，不会把超长段落整段塞入。

**方案二：Token 感知分割**
当前是字符数（500 chars），但 LLM 和 embedding 模型的限制是 token 数。中文 1 字 ≈ 1 token，英文 4 字 ≈ 1 token。用 `TokenTextSplitter` 更准确。

**方案三：语义分割（Semantic Chunking）**
计算相邻段落的 embedding 相似度，相似度骤降的地方就是天然切割点。这个方案最吸引我，因为它直接解决"话题不连贯"的问题。

**方案四：文档结构感知**
Markdown 按标题切，HTML 按标签切，代码按函数定义切。不同的文档格式有不同的天然结构。

### 2.4 ColBERT + 动态窗口

接着接触到了 ColBERT 和动态窗口这两个概念。

先说 **ColBERT**。传统密集检索（DPR）把整个 chunk 压缩成一个向量，切得不好向量就偏了。ColBERT 的做法是：保留 chunk 里每个 token 的向量，检索时 query 的每个 token 分别去找最相似的 doc token（这叫 MaxSim），求和得出总分。

```
query: "人工智能的发展" → [q1, q2, q3, q4]
doc:   "人工智能在近年来取得了..." → [d1, d2, d3, d4, d5]

评分 = MaxSim(q1, D) + MaxSim(q2, D) + MaxSim(q3, D) + MaxSim(q4, D)
     = 每个 query token 去 doc 里找最相似的那个 token
```

关键是：**chunk 里多几个无关 token 不影响评分**。所以 ColBERT 对 chunk 边界天然鲁棒。

再说**动态窗口**。它的核心是不用固定的 `[0:500], [450:950]`，而是动态决定在哪里切：

```
文档: [---话题A---][---话题A延续---][---话题B---]
                       ↑
                   检测到语义漂移，在这里切
```

判断依据可以是 embedding 相似度骤降、内容结构边界（标题）、Token 密度等。

两者结合的效果：

```
固定 500 字 + DPR:        Recall@5 ≈ 70%   ← 边界信息丢失
固定 500 字 + ColBERT:    Recall@5 ≈ 82%   ← token 级匹配缓解边界问题
动态窗口 + ColBERT:       Recall@5 ≈ 88%   ← 最佳
```

不过 ColBERT 对项目来说改造成本太高（需要替换检索架构），所以我决定先只做**动态窗口**。

### 2.5 实现思路

在现有的 `chunkDocument` 上加话题漂移检测：

```
段落列表 → 批量调用 DashScope embedding，算出每段的向量
         → 遍历段落，维护一个"运行平均中心向量"
         → 对每个新段落:
           ① 检查硬限制（超过 500 字）→ 切开
           ② 检查余弦相似度（新段落 vs 中心向量 < 阈值）→ 话题漂移，切开
         → 切开时保留 overlap 文本
```

几个关键设计：

**运行平均中心向量**
当前窗口内所有段落 embedding 的加权平均，代表这个窗口目前在说什么话题。新段落和这个中心越不相似，说明话题在漂移。

**阈值 0.7**
余弦相似度范围 [-1, 1]，0.7 是个经验值。越低越敏感（更容易切），越高越不敏感（更倾向合并）。实际使用中按文档类型调整。

**Overlap 不影响 embedding 计算**
overlap 文本只做前缀保留，不参与中心向量计算，避免污染话题检测。

**单段落文档跳过 API 调用**
如果文档只有一个段落，不调 embedding API，直接返回单个 chunk。

### 2.6 话题漂移检测的代价

这个方案不是免费的。每次 `chunkDocument` 调用会批量请求一次 DashScope embedding API（N 个段落一次调完）。带来的影响：

1. **延迟增加** — embedding API 调用大概几百毫秒到几秒，和段数有关
2. **成本增加** — embedding 按 token 计费，但 text-embedding-v2 价格很低
3. **并行时需要限流** — 多文件并行处理时，Semaphore 控制最多 2 个文件同时调 embedding

权衡下来，准确率提升远大于这些代价。

---

## 第三章：文件格式支持，从 txt 到 PDF/Word/Excel

### 3.1 发现文件格式的限制

切片逻辑看完了，回头看 `isSupportedFile` 方法：

```java
return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")
        || lower.endsWith(".yaml") || lower.endsWith(".yml")
        || lower.endsWith(".csv") || lower.endsWith(".html") || lower.endsWith(".xml");
```

一眼看过去觉得挺丰富。但仔细一想：

- `.json`、`.html`、`.xml` 是当纯文本读的，不会解析结构，进去一堆大括号和标签
- `.pdf`、`.docx`、`.xlsx` **完全不支持**
- `.csv` 也是当纯文本读，不解析行列

### 3.2 选型：PDFBox + POI

调研 Java 生态下的文件解析库：

| 格式 | 库 | 说明 |
|------|-----|------|
| PDF | Apache PDFBox 3.x | 标准库，纯 Java，PDFTextStripper 提取文本 |
| Word (docx) | Apache POI ooxml | XWPFDocument 读段落 |
| Excel (xlsx) | Apache POI ooxml | 需要 SAX 流式读取 |

### 3.3 当时纠结的问题

**用 Spring AI 自带的 DocumentReader 还是自己解析？**

Spring AI 有 `PagePdfDocumentReader`，但我的 chunking 逻辑是自定义的，用了 Spring AI 的 reader 还得把结果转成自己的流程，不如直接在 `extractText` 里用 PDFBox/POI 统一处理。

**XSSFWorkbook 还是 SAX 流式？**

Excel 最麻烦。`XSSFWorkbook` 是全量 DOM 模式，会把整张表的行、列、单元格都建在内存里。一个 50MB 的 xlsx 展开可能要 500MB 堆。所以后来改成了 SAX 流式读取。

### 3.4 SAX 流式解析 Excel 的细节

POI 的 `XSSFReader` 提供了事件驱动的 API：

```
OPCPackage.open(inputStream)
    → XSSFReader
        → getSheetsData() → Iterator<InputStream>
            每个 InputStream 是一个 sheet 的底层 XML
            → 用 SAXParser 解析
                → XSSFSheetXMLHandler 处理 startRow/cell/endRow 事件
                → 不构建 DOM，吃完一行就释放
```

这个方案的好处是：**不管 xlsx 有多大，内存消耗恒定**。代价是代码复杂度比 `XSSFWorkbook` 高不少。

### 3.5 PDF 加载的坑

PDFBox 3.x 和 2.x 的 API 不太一样。在 3.x 里：

```java
// 2.x 的写法，3.x 不支持
PDDocument.load(inputStream)

// 3.x 的正确写法
Loader.loadPDF(new RandomAccessReadBuffer(inputStream))
```

`Loader.loadPDF` 不接受 InputStream，需要包装成 `RandomAccessReadBuffer`。PDFBox 内部仍然会解析整个 PDF 结构（PDF 格式决定了必须随机访问），但至少避免了中间 `byte[]` 的额外拷贝。

---

## 第四章：大文件处理，一个隐蔽的内存问题

### 4.1 问题发现

在支持了 PDF/Word/Excel 之后，我开始想：**用户如果传一个 100MB 的 PDF 会怎样？**

分析现有代码的执行路径：

```
importFile(MultipartFile)
    → file.getBytes() → 100MB byte[]     ← 内存 1
    → extractText(filename, 100MB byte[])
        → PDFBox 解析 → PDDocument 驻留  ← 内存 2（PDDocument 更大）
        → String content ≈ 几万~几十万字符 ← 内存 3
    → FileSource(filename, content) 
    → processFiles(List.of(fileSource))
        → chunkDocument → Document 列表    ← 内存 4
        → 收集到 List<Document> allChunks  ← 内存 5（所有文件的 chunks 都攒着）
        → 遍历 allChunks 批量写入           ← 写入后才释放
```

问题很明显：**一个文件的所有 chunks 在写入前全部驻留在内存中，而且多个文件并行时叠加。** 如果同时来 4 个 100MB PDF，堆直接爆炸。

### 4.2 三处改造

我把问题拆成三个层面逐一解决：

**改造一：逐文件释放（per-file flush）**

改前：`遍历全部文件 → chunk 全部 → 批量写入全部 chunks`
改后：`遍历文件 → chunk 当前文件 → 立即写入当前 chunks → 释放 → 处理下一个文件`

这步改动最大，直接消除了 `List<Document> allChunks` 对所有文件的累积。

**改造二：流式解析**

改前：`file.getBytes()` → 全量 byte[] → 传给解析器
改后：`file.getInputStream()` → 直接传给解析器

- 纯文本：`stream.readAllBytes()` 无法避免
- PDF：`RandomAccessReadBuffer(stream)` 包装
- Word：`XWPFDocument(stream)` 直接读流
- Excel：`XSSFReader` + SAX 流式事件驱动

**改造三：Semaphore 并发限流**

并行处理时，即使每个文件单独 flush，同时处理 10 个文件还是可能撑爆。加了 Semaphore：

```java
concurrencySemaphore.acquire();
try {
    processSingleFile(file, ...);
} finally {
    concurrencySemaphore.release();
}
```

默认 `maxConcurrent=2`，再加 `ingestionExecutor` 的线程池限制（默认 4 线程），两层保护。

---

## 第五章：向量检索，理解你在做什么

### 5.1 纯向量检索的原理

项目的 `searchSimilar` 方法：

```java
vectorStore.similaritySearch(SearchRequest.builder()
    .query(message)
    .topK(topK)
    .similarityThreshold(minScore)
    .build());
```

底层做的事情：

```
用户问题 "DeepSeek R1 有什么特点"
    ↓
EmbeddingModel（DashScope text-embedding-v2）
    ↓ 转成 1536 维浮点数向量 [0.023, -0.145, ..., 0.567]
    ↓
pgvector HNSW 索引
    ↓ 余弦距离 (<=> 操作符)
SELECT text, 1 - (embedding <=> ?) AS similarity
FROM documents
WHERE 1 - (embedding <=> ?) > minScore
ORDER BY similarity DESC
LIMIT topK
    ↓
返回最相似的 chunks
```

**向量检索不做关键词匹配，做的是语义匹配。**

"怎么修电脑" 和 "电脑蓝屏如何处理" —— 语义相近，即使没有共同关键词也能匹配。
"怎么修电脑" 和 "今天天气不错" —— 语义不同，即使包含相同字也匹配不上。

### 5.2 纯向量检索的边界

知道了原理，也就知道了它的弱点：

**专有名词匹配差**
"GB/T 19001-2025" 在向量空间里的表示取决于训练数据中这个标准的出现频率。embedding 模型大概率没见过这条标准。

**复合问题容易被平均化**
用户问"训练成本和推理速度怎么样"，两个维度的信息被平均到一个向量里，可能导致两个维度都匹配不好。

**同义概念不总是对齐**
"考驾照的理论考试"在行业里叫"科目一"，embedding 模型不一定对齐这个关系。

---

## 第六章：查询优化

### 6.1 目前查询流程的问题

看 `QueryService.query()` 的实现，发现流程很简单：

```
用户问题 → 纯向量检索 → 简单拼接上下文 → LLM 回答
```

没有查询重写、没有重排序、上下文直接拼。

### 6.2 优化一：查询重写

**为什么需要重写？**

向量检索的质量很大程度上取决于 query 的质量。用户口语化问题有三个常见问题：

1. **口语填充词** —— "那个"、"到底"、"啊" → 增加噪音
2. **指代不清** —— "它的成本怎么样" → "它"指向不明
3. **复合问题** —— "训练成本和推理速度" → 两个概念被平均化

**重写怎么实现？**

直接用 DeepSeek 改写：

```java
private String rewriteQuery(String message) {
    String rewritten = chatClient.prompt()
            .system("提取核心关键词和关键概念，去除口语化表达，只输出改写后的文本")
            .user(message)
            .call()
            .content();
    if (rewritten == null || rewritten.isBlank()) return message;
    return rewritten.strip();
}
```

**什么时候不该重写？**

不是所有查询都需要重写。"DeepSeek R1 参数量是多少" —— 已经足够规范。加了 `rag.query.rewrite.enabled: true` 配置，可以按需关闭。

### 6.3 优化二：混合重排序

**问题**：纯向量检索返回的结果按向量相似度排序，但向量相似度不等于"对用户的查询最有帮助"。

**方案**：检索更多的候选（topK × 3），用混合分数重排：

```
hybridScore = 0.6 × vectorScore + 0.4 × keywordScore
```

- **vectorScore**：原始向量距离转相似度，1 - distance
- **keywordScore**：查询词在文档中出现的比例（精确匹配）

为什么仍保留 vectorScore 占大头（0.6）？因为 keywordScore 只是简单的"词出现了没"，太粗糙，不能主导排序。它只作为补充信号，在向量分接近时让更匹配关键词的文档排前面。

### 6.4 优化三：上下文压缩

`buildContext` 的改进：

1. **按 hybridScore 降序排列** —— 最相关的内容在最前面
2. **超长截断** —— 超过 `maxContextChars`（默认 8000）时截断，避免超 LLM 上下文窗口

这里的权衡是：截断后虽然上下文更完整，但可能丢失排在后位的相关信息。8000 字符对于 DeepSeek 来说远没到上限，所以截断只在文档特别多时触发。

---

## 第七章：完整的 RAG 查询链路

把前面所有的优化串起来，一条完整的查询链路：

```
用户输入
    ↓
[查询重写]  → LLM 提取核心关键词（可选，可关闭）
    ↓
[向量检索]  → DashScope embedding → pgvector HNSW 搜索 → topK × 3 候选
    ↓
[混合重排]  → 向量分 × 0.6 + 关键词重叠分 × 0.4 → 取 topK
    ↓
[上下文压缩] → 按分数降序排列 → 超过 8000 字符截断
    ↓
[LLM 生成]  → system prompt + 上下文 + 用户原问题 → DeepSeek 生成回答
    ↓
返回 QueryResponse(answer, references)
```

---

## 附录 A：配置项速查

```yaml
rag:
  ingestion:
    chunk:
      max-size: 500          # 每个 chunk 最大字符数
      overlap: 50            # 相邻 chunk 重叠字符数
      topic-threshold: 0.7   # 话题漂移检测阈值
    batch:
      size: 50               # 写入向量库的批次大小
    parallel: true           # 是否启用并行切片
    threads: 4               # 并行线程池大小
    max-concurrent: 2        # 并行时最多处理文件数

  query:
    rewrite:
      enabled: true          # 是否启用查询重写
    rerank:
      multiplier: 3          # 候选数 = topK × 这个值
      vector-weight: 0.6     # 混合排序中向量分权重
    context:
      max-chars: 8000        # 上下文最大字符数
```

## 附录 B：项目关键文件

```
rag-pgvector/
├── src/main/java/cn/project/base/ragpgvector/
│   ├── service/
│   │   ├── DocumentIngestionService.java   # 文档导入、切片、入库
│   │   └── QueryService.java               # 查询优化、检索、回答
│   ├── config/
│   │   └── RagPgConfig.java                # 线程池、ChatClient 配置
│   └── dto/
│       └── QueryResponse.java              # 响应体定义
└── src/main/resources/
    └── application.yml                     # 全部配置项
```
