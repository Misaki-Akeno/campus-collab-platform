package com.campus.common;

import com.campus.common.config.JwtProperties;
import com.campus.common.config.TraceWebMvcConfig;
import com.campus.common.util.JwtUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * campus-common 自动配置类
 * 通过 Spring Boot SPI 机制自动注册公共 Bean，确保跨服务可见
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@Import(TraceWebMvcConfig.class)
public class CommonAutoConfiguration {

    @Bean
    public JwtUtil jwtUtil(JwtProperties jwtProperties) {
        return new JwtUtil(jwtProperties);
    }
}
