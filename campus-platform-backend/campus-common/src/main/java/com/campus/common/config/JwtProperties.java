package com.campus.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 * 对应 application.yml 中 campus.jwt.* 节点
 */
@Data
@ConfigurationProperties(prefix = "campus.jwt")
public class JwtProperties {

    /** HMAC-SHA256 签名密钥（Base64编码，长度至少32字节），通过环境变量 CAMPUS_JWT_SECRET 注入 */
    private String secret;

    /** accessToken 有效期（毫秒），默认 2 小时 */
    private long accessExpire = 2 * 60 * 60 * 1000L;

    /** refreshToken 有效期（毫秒），默认 7 天 */
    private long refreshExpire = 7 * 24 * 60 * 60 * 1000L;
}
