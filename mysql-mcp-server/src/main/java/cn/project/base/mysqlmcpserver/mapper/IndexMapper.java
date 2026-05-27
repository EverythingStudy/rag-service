package cn.project.base.mysqlmcpserver.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * MyBatis Mapper — 索引信息查询。
 * <p>
 * 提供 SHOW INDEX 来查看表的索引定义，
 * 帮助诊断查询是否走了正确的索引。
 */
public interface IndexMapper {

    /**
     * 查看指定表的所有索引。
     * 返回包含：索引名、字段名、索引类型（BTREE/HASH）、唯一性、基数等。
     *
     * @param tableName 表名
     */
    @Select("SHOW INDEX FROM `${tableName}`")
    @ResultType(Map.class)
    List<Map<String, Object>> showIndexes(@Param("tableName") String tableName);
}
