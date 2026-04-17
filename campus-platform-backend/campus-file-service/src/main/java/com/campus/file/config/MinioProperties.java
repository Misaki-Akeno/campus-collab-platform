package com.campus.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Component
@ConfigurationProperties(prefix = "minio")
@Validated
public class MinioProperties {

    @NotBlank(message = "minio.endpoint 不能为空")
    private String endpoint;

    @NotBlank(message = "minio.accessKey 不能为空")
    private String accessKey;

    @NotBlank(message = "minio.secretKey 不能为空")
    private String secretKey;

    @NotBlank(message = "minio.bucketName 不能为空")
    private String bucketName;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
}
