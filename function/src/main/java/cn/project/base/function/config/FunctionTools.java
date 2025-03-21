package cn.project.base.function.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class FunctionTools {
    private static final Logger logger = LoggerFactory.getLogger(FunctionTools.class);

    public record AddOperationRequest(int d1, int d2) {
    }

    public record MulOperationRequest(int d1, int d2) {
    }

    @Bean
    @Description("加法运算")
    public Function<AddOperationRequest, Integer> addOperation() {
        return request -> {
            logger.info("加法运算函数被调用了:" + request.d1 + "," + request.d2);
            return request.d1 + request.d2;
        };
    }

    @Bean
    @Description("乘法运算")
    public Function<MulOperationRequest, Integer> mulOperation() {
        return request -> {
            logger.info("乘法运算函数被调用了:" + request.d1 + "," + request.d2);
            return request.d1 * request.d2;
        };
    }

}
