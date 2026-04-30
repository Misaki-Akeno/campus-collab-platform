package com.campus.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(exclude = {
    com.alibaba.cloud.nacos.endpoint.NacosConfigEndpointAutoConfiguration.class,
    com.alibaba.cloud.nacos.endpoint.NacosDiscoveryEndpointAutoConfiguration.class
})
@EnableDiscoveryClient(autoRegister = false)
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
