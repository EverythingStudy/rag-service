package cn.project.base.mysqlmcpserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多数据源配置属性绑定。
 * <p>
 * 对应 application.yml 中的 {@code mysql-mcp-server.datasources} 配置块。
 */
@ConfigurationProperties(prefix = "mysql-mcp-server")
public class MysqlMcpServerProperties {

    private final Map<String, DataSourceConfig> datasources;

    @ConstructorBinding
    public MysqlMcpServerProperties(@DefaultValue("{}") Map<String, DataSourceConfig> datasources) {
        this.datasources = new LinkedHashMap<>(datasources);
    }

    public Map<String, DataSourceConfig> getDatasources() {
        return datasources;
    }

    /**
     * 单个数据源的连接配置。
     */
    public static class DataSourceConfig {

        private final String url;
        private final String username;
        private final String password;
        private final PoolConfig pool;

        @ConstructorBinding
        public DataSourceConfig(String url, String username,
                                @DefaultValue("") String password,
                                @DefaultValue PoolConfig pool) {
            this.url = url;
            this.username = username;
            this.password = password;
            // 如果 YAML 中未配置 pool 块，使用默认值
            this.pool = pool != null ? pool : new PoolConfig();
        }

        public String getUrl() { return url; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public PoolConfig getPool() { return pool; }
    }

    /**
     * HikariCP 连接池参数（使用 setter 绑定，支持按需覆盖）。
     */
    public static class PoolConfig {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 10000L;
        private long maxLifetime = 1800000L;
        private long idleTimeout = 600000L;
        private long validationTimeout = 3000L;
        private long leakDetectionThreshold = 0L;

        /** 默认无参构造器 */
        public PoolConfig() {}

        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int v) { this.maximumPoolSize = v; }

        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int v) { this.minimumIdle = v; }

        public long getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(long v) { this.connectionTimeout = v; }

        public long getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(long v) { this.maxLifetime = v; }

        public long getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(long v) { this.idleTimeout = v; }

        public long getValidationTimeout() { return validationTimeout; }
        public void setValidationTimeout(long v) { this.validationTimeout = v; }

        public long getLeakDetectionThreshold() { return leakDetectionThreshold; }
        public void setLeakDetectionThreshold(long v) { this.leakDetectionThreshold = v; }
    }
}
