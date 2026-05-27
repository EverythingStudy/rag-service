package cn.project.base.mysqlmcpserver.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * MyBatis Mapper — 系统变量查询。
 * <p>
 * 提供 SHOW VARIABLES 查询，支持 LIKE 模式过滤。
 * 常用于检查 MySQL 配置，如缓冲区大小、超时设置等性能相关参数。
 */
public interface VariablesMapper {

    /**
     * 查看全部 MySQL 系统变量。
     */
    @Select("SHOW VARIABLES")
    @ResultType(Map.class)
    List<Map<String, Object>> showVariables();

    /**
     * 按 LIKE 模式过滤系统变量。
     *
     * @param pattern LIKE 匹配模式，如 {@code "%query%"} 或 {@code "%timeout%"}
     */
    @Select("SHOW VARIABLES LIKE #{pattern}")
    @ResultType(Map.class)
    List<Map<String, Object>> showVariablesLike(@Param("pattern") String pattern);
}
