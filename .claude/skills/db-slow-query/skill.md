# db-slow-query — 数据库慢查询诊断

分析 MySQL 慢查询的根本原因并给出优化建议。调用 `mysql_*` 系列 MCP 工具来获取数据库信息。

## 使用方式

在对话中描述慢查询现象或直接粘贴慢 SQL，我会自动按以下流程诊断。

## 诊断流程

### 1. 收集基础信息

按顺序执行以下探查：

- `mysql_show_tables` — 确认数据库表结构概览
- `mysql_show_variables` with pattern `'%slow%'` — 检查慢查询日志配置
- `mysql_show_variables` with pattern `'%timeout%'` — 检查超时设置
- `mysql_show_processlist` — 检查当前是否有长时间运行的查询

### 2. 分析查询结构

- `mysql_explain` — 获取查询的执行计划
- 如果 MySQL 版本 >= 8.0.18，使用 `mysql_explain_analyze` 获取带实际执行时间的分析

### 3. 检查表结构

- `mysql_describe_table` — 查看表定义
- `mysql_show_indexes` — 检查索引情况

### 4. 定位问题

根据 EXPLAIN 输出重点关注以下危险信号：

| 信号 | 含义 |
|------|------|
| `type = ALL` | 全表扫描，需检查索引 |
| `rows` 数值极大 | 扫描行数过多 |
| `Extra` 包含 `Using filesort` | 未使用索引排序 |
| `Extra` 包含 `Using temporary` | 使用了临时表（常见于 GROUP BY 优化） |
| `Extra` 包含 `Using join buffer` | JOIN 未走索引 |
| `key IS NULL` | 没有使用任何索引 |
| `possible_keys` 有值但 `key` 为 NULL | 优化器选择了全表扫描（数据分布问题或索引选择性不足） |

### 5. 深度分析

根据情况选择：

- **索引问题** → 建议创建覆盖索引或复合索引，并提供对应的 DDL
- **JOIN 问题** → 检查关联字段是否有索引、数据类型是否一致
- **数据量问题** → 建议分页、分区或归档策略
- **锁等待** → 再次执行 `mysql_show_processlist`，结合 `%lock%` 变量排查
- **配置问题** → 检查 `innodb_buffer_pool_size`、`sort_buffer_size` 等关键变量

### 6. 输出优化报告

以如下格式输出诊断结果：

```markdown
## 慢查询诊断报告

**问题SQL：** `SELECT ...`

### 执行计划
[EXPLAIN 输出]

### 关键发现
- 🔴 全表扫描：table `xxx` (rows: 100000)
- 🟡 Using filesort：未使用索引排序

### 优化建议
1. [具体建议 1]
2. [具体建议 2]

### 优化后的 SQL
\`\`\`sql
...
\`\`\`
```

## 安全约束

- 只执行 `SELECT / SHOW / DESCRIBE / EXPLAIN / WITH` 只读查询
- 不修改数据库结构和数据
- 不分析包含敏感信息的查询（密码、Token 等）
