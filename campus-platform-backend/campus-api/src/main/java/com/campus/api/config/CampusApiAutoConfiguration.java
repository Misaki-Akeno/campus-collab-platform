package com.campus.api.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * campus-api 自动配置：通过 Spring Boot SPI 自动扫描本包下所有 FeignClient。
 * <p>
 * 消费方服务引入 campus-api 依赖后，无需手动声明 @EnableFeignClients，
 * 该配置会通过 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 自动生效。
 * {@code @AutoConfiguration} 隐含 @Configuration，同时提供 Spring Boot 3 所需的 ordering/conditional 语义。
 * </p>
 */
@AutoConfiguration
@EnableFeignClients(basePackages = "com.campus.api")
public class CampusApiAutoConfiguration {
}
