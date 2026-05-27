package cn.project.base.mysqlmcpserver.mapper;

import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * MyBatis Mapper — 进程列表查询。
 * <p>
 * 查看当前 MySQL 连接和正在执行的查询，
 * 用于发现长时间运行的慢查询、锁等待等问题。
 */
public interface ProcessListMapper {

    /**
     * 查看当前进程列表（SQL 截断前 100 字符）。
     */
    @Select("SHOW PROCESSLIST")
    @ResultType(Map.class)
    List<Map<String, Object>> showProcessList();

    /**
     * 查看当前进程列表（显示完整 SQL 语句）。
     */
    @Select("SHOW FULL PROCESSLIST")
    @ResultType(Map.class)
    List<Map<String, Object>> showFullProcessList();
}
