package cn.project.base.langchainai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
@Slf4j
public class LangchainAiApplication {

    public static void main(String[] args) {
        //SpringApplication.run(LangchainAiApplication.class, args);
        ConfigurableApplicationContext application = SpringApplication.run(LangchainAiApplication.class, args);
        Environment env = application.getEnvironment();
        String port = env.getProperty("server.port");
        log.info("Application启动成功，服务端口为：" + port);
    }

}
