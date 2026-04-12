package com.campus.club;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.campus.club", "com.campus.common"})
@EnableDiscoveryClient
@MapperScan("com.campus.club.mapper")
public class ClubServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClubServiceApplication.class, args);
    }
}
