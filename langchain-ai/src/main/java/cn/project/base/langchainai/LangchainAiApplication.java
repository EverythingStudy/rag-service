package cn.project.base.langchainai;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class LangchainAiApplication {
    private static final Logger log = LoggerFactory.getLogger(LangchainAiApplication.class);

    public static void main(String[] args) {
        //SpringApplication.run(LangchainAiApplication.class, args);
        ConfigurableApplicationContext application = SpringApplication.run(LangchainAiApplication.class, args);
        Environment env = application.getEnvironment();
        String port = env.getProperty("server.port");
        log.info("\n----------------------------------------------------------\n\t" +
                "Application '{}' is running! Access URLs:\n\t" +
                "Local: \t\thttp://localhost:{}\n\t" +
                "External: \thttp://{}:{}\n----------------------------------------------------------",
                env.getProperty("spring.application.name"),
                port,
                "127.0.0.1",
                port);
    }

}
