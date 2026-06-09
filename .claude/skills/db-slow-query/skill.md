---
name: db-slow-query
description: "TRIGGER when: 用户提到\"慢查询\"\"慢SQL\"\"查询慢\"\"SQL优化\"\"执行计划\"\"索引优化\"\"数据库卡\"\"查询超时\"\"SQL性能\"\"explain\"、\"response slow\"、\"query timeout\"。SKIP: 用户只是查询普通数据不需要诊断；问题涉及数据库写入优化（INSERT/UPDATE/DELETE 慢）；问题涉及数据库配置变更（需要手动执行 SQL 修改）；涉及非 MySQL 数据库。"
---

# db-slow-query — 数据库慢查询诊断

分析 MySQL 慢查询的根本原因并给出优化建议。通过 `mysql-mcp-server` 提供的 MCP 工具获取数据库诊断信息。

---

## 使用方式

在对话中描述慢查询现象或直接粘贴慢 SQL，我会自动按以下流程诊断。

**可用数据源：** 先调用 `mysql_list_datasources` 确认有哪些数据库实例可用。
所有诊断工具都接受 `datasource` 参数（默认: `"default"`）。

## 诊断流程

### 1. 收集基础信息

按顺序执行以下探查：

- `mysql_show_tables` — 确认数据库表结构概览
- `mysql_show_variables` 带 `pattern: '%slow%'` — 检查慢查询日志配置
- `mysql_show_variables` 带 `pattern: '%timeout%'` — 检查超时设置
- `mysql_show_processlist` 带 `full: true` — 检查当前是否有长时间运行的查询
- `mysql_show_table_status` — 查看各表的行数、数据大小、引擎类型

### 2. 分析查询结构

- `mysql_explain` — 获取查询的执行计划
- 如果 MySQL 版本 >= 8.0.18，使用 `mysql_explain_analyze` 获取带实际执行时间的分析

### 3. 检查表结构

- `mysql_describe_table` — 查看表定义（字段名、类型、NULL 约束等）
- `mysql_show_indexes` — 检查索引情况（主键、唯一索引、普通索引、复合索引）

### 4. 定位问题

根据 EXPLAIN 输出重点检查以下危险信号：

| 信号 | 严重程度 | 含义 |
|------|---------|------|
| `type = ALL` | 🔴 高 | 全表扫描，需检查索引 |
| `rows` 数值极大 | 🔴 高 | 扫描行数过多 |
| `Extra` 包含 `Using filesort` | 🟡 中 | 未使用索引排序 |
| `Extra` 包含 `Using temporary` | 🟡 中 | 使用了临时表（常见于 GROUP BY） |
| `Extra` 包含 `Using join buffer` | 🟡 中 | JOIN 未走索引 |
| `key IS NULL` | 🔴 高 | 没有使用任何索引 |
| `possible_keys` 有值但 `key` 为 NULL | 🟡 中 | 优化器选择了全表扫描（数据分布或索引选择性不足） |

### 5. 深度分析

根据发现的信号选择对应策略：

#### 索引问题
- 建单列索引还是复合索引？看 WHERE + ORDER BY 涉及的字段
- 建议和对应的 DDL：`CREATE INDEX ... ON table (col1, col2)`
- 注意复合索引的最左前缀原则

#### JOIN 问题
- 检查关联字段是否有索引
- 检查关联字段的数据类型是否一致（隐式转换会导致索引失效）
- 小表驱动大表

#### 数据量问题
- 数据量大 + 全表扫描 → 建议分页 `LIMIT` 优化
- 数据量超大 → 建议分区策略（RANGE/HASH/LIST）
- 历史数据归档

#### 锁等待
- 再次执行 `mysql_show_processlist` 观察状态为 `Waiting for table lock` 的会话
- 结合 `mysql_show_variables` 带 `pattern: '%lock%'` 排查锁超时设置

#### 配置问题
- `mysql_show_variables` 带 `pattern: 'innodb_buffer_pool_size'` — 检查 InnoDB 缓冲池大小
- `mysql_show_variables` 带 `pattern: 'sort_buffer_size'` — 检查排序缓冲区
- `mysql_show_variables` 带 `pattern: 'join_buffer_size'` — 检查 JOIN 缓冲区

### 6. 常见场景速查

| 场景 | 典型 EXPLAIN 特征 | 建议 |
|------|-------------------|------|
| 分页偏移量过大 | `type: ALL`, `Extra: Using filesort` | 使用游标分页（WHERE id > ?）代替 OFFSET |
| LIKE 前导通配符 | `type: ALL`, `possible_keys` 但 `key: NULL` | 避免 `LIKE '%keyword%'`，改用全文索引 |
| OR 条件 | `type: ALL` 或 `type: index_merge` | 改为 UNION 或用 IN 替代 |
| NOT IN 子查询 | `type: ALL`, `Extra: Using where` | 改为 LEFT JOIN ... IS NULL 或 NOT EXISTS |
| ORDER BY 未走索引 | `Extra: Using filesort` | 为排序字段加索引 |
| GROUP BY 无索引 | `Extra: Using temporary; Using filesort` | 为 GROUP BY + ORDER BY 建复合索引 |

### 7. 输出优化报告

以如下格式输出诊断结果：

```markdown
## 慢查询诊断报告

**问题SQL：** `SELECT ...`

### 执行计划
```
[EXPLAIN 输出]
```

### 关键发现
- 🔴 全表扫描：table `xxx` (rows: 100000)
- 🟡 Using filesort：未使用索引排序

### 优化建议
1. [具体建议 1]
2. [具体建议 2]

### 优化后的 SQL
```sql
...
```
```

## 安全约束

- 只执行 `SELECT / SHOW / DESCRIBE / EXPLAIN / WITH` 只读查询
- 不修改数据库结构和数据
- 不分析包含敏感信息的查询（密码、Token 等）
- 所有诊断操作通过 `mysql_query` 工具的安全检查，非只读语句会被拦截
