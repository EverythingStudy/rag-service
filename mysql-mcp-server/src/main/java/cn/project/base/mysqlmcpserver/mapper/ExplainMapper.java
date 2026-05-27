package cn.project.base.mysqlmcpserver.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * MyBatis Mapper — 查询执行计划。
 * <p>
 * 使用 {@code ${query}} 直接拼接 EXPLAIN 语句（非预编译），
 * 因为 MySQL EXPLAIN 的语法不支持参数化占位符。
 * <p>
 * 安全说明：此服务仅用于数据库诊断场景，调用者需具备对应权限。
 */
public interface ExplainMapper {

    /**
     * 获取普通 EXPLAIN 执行计划。
     *
     * @param query 原始 SELECT 查询语句
     * @return EXPLAIN 结果集
     */
    @Select("EXPLAIN ${query}")
    @ResultType(Map.class)
    List<Map<String, Object>> explain(@Param("query") String query);

    /**
     * 获取 EXPLAIN ANALYZE 执行计划（MySQL 8.0.18+ 支持）。
     * 比普通 EXPLAIN 提供更多运行时信息：实际执行时间、循环次数等。
     *
     * @param query 原始 SELECT 查询语句
     * @return EXPLAIN ANALYZE 结果集
     */
    @Select("EXPLAIN ANALYZE ${query}")
    @ResultType(Map.class)
    List<Map<String, Object>> explainAnalyze(@Param("query") String query);
}
