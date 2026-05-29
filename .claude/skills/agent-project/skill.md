# agent-project — 项目架构与设计意图

基于 Java + Spring AI 构建可扩展的 AI Agent 运行时，让 LLM 通过 Skill、MCP 工具、RAG 知识库等能力与外部系统交互。

## 核心模块

| 模块 | 定位 |
|------|------|
| **spring-ai-agent-runtime** | AI Agent 运行时核心 — 编排 LLM + Skill + MCP + RAG + Memory |
| **mysql-mcp-server** | MySQL MCP Server — 通过 MCP 协议暴露数据库诊断工具 |
| rag-pgvector | RAG 服务（PGVector 实现），可被 agent-runtime 调用 |
| 其他模块 | function / spring-ai-model / rag-service / 均为独立实验模块，不修改 |

## 技术栈

- Java 21 + Spring Boot 3.2.0 + Spring AI 1.0.0-M6
- MCP 通信: HTTP/SSE (Server-Sent Events)
- LLM: DeepSeek (通过 OpenAI 兼容 API)
- Embedding: Ollama 本地模型
- 向量库: SimpleVectorStore（开发）/ 可切换 PGVector/Chroma

## Agent Runtime 架构

```
用户请求
    │
    ▼
AgentController → AgentService → AgentOrchestrator
                                      │
                           ┌──────────┼──────────┐
                           ▼          ▼          ▼
                       Skill    MCP Tools     RAG/Knowledge
                     (@Tool)   (通过SSE)     (VectorStore)
```

### 工具注入机制

- `List<ToolCallbackProvider>` 自动收集所有 ToolCallbackProvider 类型的 Bean
- 内置 Skill: 通过 `SkillConfig` → `MethodToolCallbackProvider` 注册
- MCP 工具: `spring-ai-mcp-client-webflux-spring-boot-starter` 自动发现并注册
- 所有工具统一注入给 `ChatClient.defaultTools()`

## 扩展方式

| 扩展类型 | 做法 |
|----------|------|
| **新增 Skill** | 创建 `@Service` + `@Tool` 类，在 `SkillConfig` 中注册 |
| **新增 MCP Server** | 在 `application.yml` → `mcp.client.sse.connections` 中添加 |
| **切换/新增向量库** | 替换 `VectorStoreConfig` 的实现（如 → PGVectorStore） |
| **新增 Controller** | 在 `controller/` 包下创建，按业务拆分 |
| **新增 Domain** | 在 `domain/` 包下创建 |

## 关键约定

1. 所有 Skill 放在 `skill/builtin/` 包下，通过 `SkillConfig` 注册
2. 所有配置类放在 `config/` 包下
3. 领域对象放在 `domain/` 包下
4. MCP 工具通过配置开启，不修改 AgentConfig 核心代码
5. **不修改 pom.xml 中已有的 Spring AI BOM 版本**
6. **不修改与当前任务无关的模块**

## 启动顺序

1. mysql-mcp-server（`cd mysql-mcp-server && mvn spring-boot:run`，8082 端口）
2. spring-ai-agent-runtime（`cd spring-ai-agent-runtime && mvn spring-boot:run`，8099 端口）
3. 调用 `POST /api/agent/chat {"message": "你的问题"}`

## 未来可扩展模块

- Gateway: API 网关层，统一认证、限流、路由
- Memory 持久化: 从 InMemoryChatMemory → Redis/数据库
- 更多 MCP Server: Redis、PostgreSQL、Elasticsearch
- Agent 链路追踪: 可视化工具调用的完整链路
