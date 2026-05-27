package cn.project.base.mysqlmcpserver;

import cn.project.base.mysqlmcpserver.config.MysqlMcpServerProperties;
import cn.project.base.mysqlmcpserver.service.MySQLDatabaseService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MySQL MCP Server 启动入口。
 * <p>
 * 基于 Spring AI MCP Server WebFlux Starter，通过 HTTP/SSE 协议
 * 暴露 MySQL 数据库查询工具。支持多数据源连接池和 MyBatis 预定义查询。
 */
@SpringBootApplication
@EnableConfigurationProperties(MysqlMcpServerProperties.class)
public class MysqlMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MysqlMcpServerApplication.class, args);
    }

    /**
     * 将 MySQLDatabaseService 中的 @Tool 方法注册为 MCP 工具。
     */
    @Bean
    public ToolCallbackProvider mysqlTools(MySQLDatabaseService mysqlDatabaseService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mysqlDatabaseService)
                .build();
    }
}
