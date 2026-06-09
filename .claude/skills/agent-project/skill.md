---
name: agent-project
description: "TRIGGER when: 用户询问项目架构、模块职责、扩展方式、启动顺序、如何新增 Skill/MCP/向量库。SKIP: 用户询问具体代码实现细节、单个类的内部逻辑、业务功能开发。"
---

# agent-project — 项目架构与设计意图

基于 Java + Spring AI 构建可扩展的 AI Agent 运行时，让 LLM 通过 Skill、MCP 工具、RAG 知识库等能力与外部系统交互。

---

## 核心模块

| 模块 | 定位 | 端口 |
|------|------|------|
| **spring-ai-agent-runtime** | AI Agent 运行时核心 — 编排 LLM + Skill + MCP + RAG + Memory | 8099 |
| **mysql-mcp-server** | MySQL MCP Server — 通过 MCP 协议暴露数据库诊断工具 | 8082 |
| **rag-pgvector** | RAG 服务（PGVector 实现），可被 agent-runtime 调用 | 动态 |
| 其他模块 | function / spring-ai-model / rag-service — 独立实验模块，不修改 | — |

## 技术栈

- Java 21 + Spring Boot 3.2.0 + Spring AI 1.0.0-M6
- MCP 通信: HTTP/SSE (Server-Sent Events)
- LLM: DeepSeek（通过 OpenAI 兼容 API）
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

## 当前内置 Skill

| Skill 名称 | 类 | 功能 |
|-----------|-----|------|
| 天气查询 | `WeatherSkill` | 获取指定经纬度的天气和预报（基于 Open-Meteo 免费 API） |
| 时间日期 | `TimeSkill` | 获取当前时间/日期、计算日期差（支持时区） |
| 知识库搜索 | `KnowledgeSearchSkill` | 从 RAG 知识库中检索相关信息 |

### Skill 注册方式

所有 Skill 在 `SkillConfig.java` 中通过 `MethodToolCallbackProvider` 注册：

```java
@Bean
public ToolCallbackProvider xxxProvider(XxxSkill skill) {
    return MethodToolCallbackProvider.builder().toolObjects(skill).build();
}
```

## 可用 MCP 工具（mysql-mcp-server）

通过 MySQL 数据库服务暴露的诊断工具，支持多数据源切换（`datasource` 参数）：

| 工具名 | 功能 |
|--------|------|
| `mysql_query` | 执行只读 SQL（SELECT/SHOW/DESCRIBE/EXPLAIN/WITH） |
| `mysql_explain` | 获取 EXPLAIN 执行计划 |
| `mysql_explain_analyze` | 获取 EXPLAIN ANALYZE（MySQL 8.0.18+） |
| `mysql_show_tables` | 列出所有表 |
| `mysql_describe_table` | 查看表列定义 |
| `mysql_show_table_status` | 查看表状态（行数、数据大小等） |
| `mysql_show_indexes` | 查看表索引定义 |
| `mysql_show_processlist` | 查看当前连接和查询 |
| `mysql_show_variables` | 查看系统变量（支持 LIKE 过滤） |
| `mysql_list_datasources` | 查看可用数据源列表 |

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

```bash
# 1. 启动 MySQL MCP Server（8082 端口）
cd mysql-mcp-server && mvn spring-boot:run

# 2. 启动 Agent Runtime（8099 端口）
cd spring-ai-agent-runtime && mvn spring-boot:run

# 3. 调用
curl -X POST http://localhost:8099/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你的问题"}'
```

## 未来可扩展模块

- Gateway: API 网关层，统一认证、限流、路由
- Memory 持久化: 从 InMemoryChatMemory → Redis/数据库
- 更多 MCP Server: Redis、PostgreSQL、Elasticsearch
- Agent 链路追踪: 可视化工具调用的完整链路
