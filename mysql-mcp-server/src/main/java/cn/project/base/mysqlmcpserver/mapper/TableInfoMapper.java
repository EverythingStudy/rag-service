package cn.project.base.mysqlmcpserver.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * MyBatis Mapper — 表信息查询。
 * <p>
 * 包含 SHOW TABLES、DESCRIBE、SHOW TABLE STATUS 等元数据查询。
 */
public interface TableInfoMapper {

    /**
     * 列出当前数据库中所有表。
     */
    @Select("SHOW TABLES")
    @ResultType(Map.class)
    List<Map<String, Object>> showTables();

    /**
     * 查看指定表的列定义（字段名、类型、是否为空、默认值、额外信息）。
     *
     * @param tableName 表名
     */
    @Select("DESCRIBE `${tableName}`")
    @ResultType(Map.class)
    List<Map<String, Object>> describeTable(@Param("tableName") String tableName);

    /**
     * 查看所有表的详细状态信息。
     * 包含行数、数据大小、索引大小、创建时间、表引擎等。
     */
    @Select("SHOW TABLE STATUS")
    @ResultType(Map.class)
    List<Map<String, Object>> showTableStatus();
}
