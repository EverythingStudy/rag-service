package cn.project.base.springadminservice;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAdminServer
public class SpringAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAdminServiceApplication.class, args);
    }

}
