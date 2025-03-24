package cn.project.base.springadminservice.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 配置安全认证：创建一个配置类，并配置Spring Security的相关设置
 * 6.4版本移除WebSecurityConfigurerAdapter 使用SecurityFilterChain替换
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/", "/home").permitAll()
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .loginPage("/login")
                .permitAll()
                .and()
                .logout()
                .permitAll();
        return http.build();
    }
//    @Override
//    protected void configure(HttpSecurity http) throws Exception {
//        http
//                .authorizeRequests()
//                .antMatchers("/login", "/assets/**", "/manage/**", "/actuator/**").permitAll()  // 允许访问的URL路径
//                .anyRequest().authenticated()  // 其他请求需要认证
//                .and()
//                .formLogin()  // 表单登录
//                .loginPage("/login")  // 登录页面
//                .permitAll()
//                .and()
//                .logout()  // 注销
//                .logoutUrl("/logout")  // 注销URL
//                .permitAll()
//                .and()
//                .csrf().disable();  // 禁用CSRF保护（仅用于示例，生产环境中应启用）
//    }

    /**
     * 在上面的示例中，{noop}表示明文密码。在生产环境中，应使用更安全的密码加密方式。
     *
     * @param auth
     * @throws Exception
     */
    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .inMemoryAuthentication()  // 内存中的用户认证
                .withUser("admin").password("{noop}admin").roles("ADMIN");  // 配置用户和密码（注意：这里使用了明文密码，生产环境中应使用加密密码）
    }
}

