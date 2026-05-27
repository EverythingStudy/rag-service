package cn.project.base.mysqlmcpserver.service;

import cn.project.base.mysqlmcpserver.mapper.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MySQL MCP 工具服务 — @Tool 门面。
 * <p>
 * 每个 @Tool 方法对应一个 MCP 工具，方法职责：
 * <ul>
 *   <li>接受 AI 模型传入的参数（查询语句、数据源选择等）</li>
 *   <li>预定义诊断查询 → 路由到对应的 MyBatis Mapper 执行</li>
 *   <li>用户任意 SQL → 使用 JdbcTemplate 执行</li>
 *   <li>结果通过 {@link ResultFormatter} 格式化为 ASCII 表格返回</li>
 * </ul>
 * <p>
 * 所有方法支持 {@code datasource} 参数来选择目标数据库实例，
 * 默认为 {@code "default"}。
 */
@Service
public class MySQLDatabaseService {

    private final DatabaseConnectionManager connectionManager;

    public MySQLDatabaseService(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 执行只读 SQL 查询。
     * 使用 JdbcTemplate 直接执行，因为查询语句是动态的用户输入，
     * 无法通过预定义 Mapper 处理。
     */
    @Tool(description = "执行只读 SQL 查询（SELECT/SHOW/DESCRIBE/EXPLAIN/WITH），返回格式化结果集")
    public String mysql_query(
            @ToolParam(description = "SQL 查询语句") String query,
            @ToolParam(description = "数据源名称（默认: default）") String datasource) {
        String ds = (datasource != null && !datasource.isEmpty()) ? datasource : "default";
        String trimmed = query.trim().toUpperCase();

        // 安全检查：只允许只读语句
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("SHOW")
                && !trimmed.startsWith("DESCRIBE") && !trimmed.startsWith("DESC")
                && !trimmed.startsWith("EXPLAIN") && !trimmed.startsWith("WITH")) {
            return "ERROR: 只允许 SELECT / SHOW / DESCRIBE / EXPLAIN / WITH 查询";
        }

        try {
            List<Map<String, Object>> rows = connectionManager.getJdbcTemplate(ds).queryForList(query);
            return ResultFormatter.format(rows);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 获取 SELECT 查询的 EXPLAIN 执行计划。
     * 走 MyBatis Mapper 执行预定义 EXPLAIN 语句。
     */
    @Tool(description = "获取 SELECT 查询的 EXPLAIN 执行计划，分析查询性能瓶颈")
    public String mysql_explain(
            @ToolParam(description = "需要分析的 SELECT 查询") String query,
            @ToolParam(description = "数据源名称（默认: default）") String datasource) {
        String ds = (datasource != null && !datasource.isEmpty()) ? datasource : "default";
        try {
            ExplainMapper mapper = connectionManager.getMapper(ds, ExplainMapper.class);
            return ResultFormatter.format(mapper.explain(query));
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 获取 SELECT 查询的 EXPLAIN ANALYZE 结果（MySQL 8.0.18+）。
     * 比普通 EXPLAIN 提供实际执行时间和成本的详细信息。
     */
    @Tool(description = "获取 SELECT 查询的 EXPLAIN ANALYZE（MySQL 8.0.18+），包含实际执行时间和成本")
    public String mysql_explain_analyze(
            @ToolParam(description = "需要分析的 SELECT 查询") String query,
            @ToolParam(description = "数据源名称（默认: default）") String datasource) {
        String ds = (datasource != null && !datasource.isEmpty()) ? datasource : "default";
        try {
            ExplainMapper mapper = connectionManager.getMapper(ds, ExplainMapper.class);
            return ResultFormatter.format(mapper.explainAnalyze(query));
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 列出当前数据库中的所有表。
     */
    @Tool(description = "列出当前数据库中的所有表")
    public String mysql_show_tables(
            @ToolParam(description = "数据源名称（默认: default）") String datasource) {
        String ds = (datasource != null && !datasource.isEmpty()) ? datasource : "default";
        try {
            TableInfoMapper mapper = connectionManager.getMapper(ds, TableInfoMapper.class);
            return ResultFormatter.format(mapper.showTables());
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 查看指定表的列定义信息。
     */
    @Tool(description = "查看指定表的列定义（字段名、类型、是否为NULL、默认值等）")
    public String mysql_describe_table(
            @ToolParam(description = "表名") String tableName,
            @ToolParam(description = "数据源名称（默认: default）") String datasource) {
        String ds = (datasource != null && !datasource.isEmpty()) ? datasource : "default";
        try {
            TableInfoMapper mapper = connectionManager.getMapper(ds, TableInfoMapper.class);
            return ResultFormatter.format(mapper.describeTable(tableName));
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 查看所有表的详细状态信息：行数、数据大小、索引大小、引擎等。
     */
    @Tool(description = "查看所有表的详细状态（行数、数据大小、索引大小、引擎类型等）")
    public String mysql_show_table_status(
            @ToolParam(description = "数据源名称（默认: default）") String datasource) {
        String ds = (datasource != null && !datasource.isEmpty()) ? datasource : "default";
        try {
            TableInfoMapper mapper = connectionManager.getMapper(ds, TableInfoMapper.class);
            return ResultFormatter.format(mapper.showTableStatus());
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 查看指定表的索引信息。
     */
    @Tool(description = "查看指定表的索引定义")
    public String mysql_show_indexes(
            @ToolParam(description = "表名") String tableName,
            @ToolParam(description = "数据源名称（默认: default）") String datasource) {
        String ds = (datasource != null && !datasource.isEmpty()) ? datasource : "default";
        try {
            IndexMapper mapper = connectionManager.getMapper(ds, IndexMapper.class);
            return ResultFormatter.format(mapper.showIndexes(tableName));
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 查看当前 MySQL 进程列表。用于发现慢查询和锁等待。
     */
    @Tool(description = "查看当前 MySQL 连接和正在执行的查询")
    public String mysql_show_processlist(
            @ToolParam(description = "是否显示完整 SQL 语句") Boolean full,
            @ToolParam(description = "数据源名称（默认: default）") String datasource) {
        String ds = (datasource != null && !datasource.isEmpty()) ? datasource : "default";
        try {
            ProcessListMapper mapper = connectionManager.getMapper(ds, ProcessListMapper.class);
            if (full != null && full) {
                return ResultFormatter.format(mapper.showFullProcessList());
            }
            return ResultFormatter.format(mapper.showProcessList());
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 查看 MySQL 系统变量。支持 LIKE 模式过滤。
     */
    @Tool(description = "查看 MySQL 系统变量，支持 LIKE 模式过滤")
    public String mysql_show_variables(
            @ToolParam(description = "LIKE 匹配模式，如 '%query%' 或 '%timeout%'，为空返回全部") String pattern,
            @ToolParam(description = "数据源名称（默认: default）") String datasource) {
        String ds = (datasource != null && !datasource.isEmpty()) ? datasource : "default";
        try {
            VariablesMapper mapper = connectionManager.getMapper(ds, VariablesMapper.class);
            if (pattern != null && !pattern.isEmpty()) {
                return ResultFormatter.format(mapper.showVariablesLike(pattern));
            }
            return ResultFormatter.format(mapper.showVariables());
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 获取可用数据源列表。
     */
    @Tool(description = "查看当前可用的所有数据源列表")
    public String mysql_list_datasources() {
        try {
            return "可用数据源: " + String.join(", ", connectionManager.getAvailableDatasources());
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
