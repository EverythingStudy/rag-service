# PostgreSQL pgvector 使用指南

pgvector 是 PostgreSQL 的向量相似度搜索扩展。

## 安装

在 PostgreSQL 中创建扩展：
```sql
CREATE EXTENSION vector;
```

## 连接参数

连接 pgvector 数据库需要以下参数：
- host: localhost
- port: 5432
- database: ragdb
- username: postgres
- password: postgres

## Spring AI 配置

在 Spring AI 中使用 PgVectorStore：
```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimension: 768
        initialize-schema: true
```

## 支持的索引类型

- IVFFlat：倒排文件索引，适合大规模数据集
- HNSW：分层可导航小世界图索引，查询速度更快

## 支持的距离类型

- Euclidean (L2)
- Cosine Distance
- Inner Product (IP)
