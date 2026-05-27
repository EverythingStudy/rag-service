package cn.project.base.mysqlmcpserver.service;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多数据源连接池管理器。
 * <p>
 * 管理多个 MySQL 数据源的连接池和 MyBatis SqlSessionTemplate。
 * 提供以下能力：
 * <ul>
 *   <li>通过 {@link #getJdbcTemplate(String)} 获取 JdbcTemplate（用于 mysql_query）</li>
 *   <li>通过 {@link #getMapper(String, Class)} 获取 MyBatis Mapper（用于预定义诊断查询）</li>
 *   <li>通过 {@link #getAvailableDatasources()} 列出所有可用数据源</li>
 * </ul>
 */
public class DatabaseConnectionManager {

    /** 数据源名称 -> DataSource 映射 */
    private final Map<String, DataSource> dataSources;

    /** 数据源名称 -> MyBatis SqlSessionTemplate 映射（线程安全） */
    private final Map<String, SqlSessionTemplate> sqlSessionTemplates;

    /** 数据源名称 -> JdbcTemplate 缓存 */
    private final Map<String, JdbcTemplate> jdbcTemplateCache = new ConcurrentHashMap<>();

    public DatabaseConnectionManager(Map<String, DataSource> dataSources,
                                     Map<String, SqlSessionFactory> sqlSessionFactories) {
        this.dataSources = Collections.unmodifiableMap(dataSources);

        Map<String, SqlSessionTemplate> templates = new ConcurrentHashMap<>();
        for (var entry : sqlSessionFactories.entrySet()) {
            templates.put(entry.getKey(), new SqlSessionTemplate(entry.getValue()));
        }
        this.sqlSessionTemplates = Collections.unmodifiableMap(templates);
    }

    /**
     * 获取指定数据源的 MyBatis Mapper 实例。
     *
     * @param datasourceName 数据源名称
     * @param mapperClass    Mapper 接口类型
     * @param <T>            Mapper 类型
     * @return Mapper 实例
     */
    public <T> T getMapper(String datasourceName, Class<T> mapperClass) {
        SqlSessionTemplate template = sqlSessionTemplates.get(datasourceName);
        if (template == null) {
            throw new IllegalArgumentException("未知数据源: " + datasourceName
                    + "，可用: " + getAvailableDatasources());
        }
        return template.getMapper(mapperClass);
    }

    /**
     * 获取指定数据源的 JdbcTemplate，用于执行任意 SQL。
     * <p>
     * JdbcTemplate 是轻量对象，首次创建后缓存复用。
     */
    public JdbcTemplate getJdbcTemplate(String datasourceName) {
        return jdbcTemplateCache.computeIfAbsent(datasourceName, name -> {
            DataSource ds = dataSources.get(name);
            if (ds == null) {
                throw new IllegalArgumentException("未知数据源: " + name
                        + "，可用: " + getAvailableDatasources());
            }
            return new JdbcTemplate(ds);
        });
    }

    /**
     * 列出所有已配置的数据源名称。
     */
    public Set<String> getAvailableDatasources() {
        return dataSources.keySet();
    }
}
