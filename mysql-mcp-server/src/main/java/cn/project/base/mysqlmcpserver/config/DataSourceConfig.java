package cn.project.base.mysqlmcpserver.config;

import cn.project.base.mysqlmcpserver.mapper.*;
import cn.project.base.mysqlmcpserver.service.DatabaseConnectionManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多数据源配置。
 * <p>
 * 读取 {@link MysqlMcpServerProperties} 中的配置，
 * 为每个数据源创建独立的 HikariCP 连接池和 MyBatis SqlSessionFactory。
 */
@Configuration
public class DataSourceConfig {

    private final MysqlMcpServerProperties properties;

    public DataSourceConfig(MysqlMcpServerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public DatabaseConnectionManager databaseConnectionManager() {
        Map<String, DataSource> dataSourceMap = new LinkedHashMap<>();
        Map<String, SqlSessionFactory> factoryMap = new LinkedHashMap<>();

        // 遍历配置，为每个数据源创建连接池和 MyBatis 工厂
        for (var entry : properties.getDatasources().entrySet()) {
            String name = entry.getKey();
            var dsConfig = entry.getValue();

            // ── 1. 创建 HikariCP 连接池 ──────────────────────────
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(dsConfig.getUrl());
            hikariConfig.setUsername(dsConfig.getUsername());
            hikariConfig.setPassword(dsConfig.getPassword());
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // 连接池参数
            var pool = dsConfig.getPool();
            hikariConfig.setMaximumPoolSize(pool.getMaximumPoolSize());
            hikariConfig.setMinimumIdle(pool.getMinimumIdle());
            hikariConfig.setConnectionTimeout(pool.getConnectionTimeout());
            hikariConfig.setMaxLifetime(pool.getMaxLifetime());
            hikariConfig.setIdleTimeout(pool.getIdleTimeout());
            hikariConfig.setValidationTimeout(pool.getValidationTimeout());
            if (pool.getLeakDetectionThreshold() > 0) {
                hikariConfig.setLeakDetectionThreshold(pool.getLeakDetectionThreshold());
            }

            // 连接池命名（方便监控）
            hikariConfig.setPoolName("HikariPool-" + name);
            // 连接验证
            hikariConfig.setConnectionTestQuery("SELECT 1");
            hikariConfig.setConnectionInitSql("SET NAMES utf8mb4");

            HikariDataSource dataSource = new HikariDataSource(hikariConfig);
            dataSourceMap.put(name, dataSource);

            // ── 2. 创建 MyBatis SqlSessionFactory ────────────────
            // 每个数据源独立工厂，注册全部预定义查询 Mapper
            org.apache.ibatis.session.Configuration mybatisConfig =
                    new org.apache.ibatis.session.Configuration();
            mybatisConfig.setEnvironment(new Environment(name + "-env",
                    new JdbcTransactionFactory(), dataSource));
            mybatisConfig.setMapUnderscoreToCamelCase(true);

            // 注册 Mapper 接口（所有数据源共享同一组 Mapper 定义）
            mybatisConfig.addMapper(ExplainMapper.class);
            mybatisConfig.addMapper(TableInfoMapper.class);
            mybatisConfig.addMapper(IndexMapper.class);
            mybatisConfig.addMapper(ProcessListMapper.class);
            mybatisConfig.addMapper(VariablesMapper.class);

            factoryMap.put(name, new DefaultSqlSessionFactory(mybatisConfig));
        }

        return new DatabaseConnectionManager(dataSourceMap, factoryMap);
    }
}
