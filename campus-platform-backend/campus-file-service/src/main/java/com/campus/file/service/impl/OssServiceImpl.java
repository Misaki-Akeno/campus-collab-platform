package com.campus.file.service.impl;

import com.campus.file.config.MinioProperties;
import com.campus.file.service.OssService;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OssServiceImpl implements OssService {

    private static final Logger log = LoggerFactory.getLogger(OssServiceImpl.class);

    private final MinioClient minioClient;
    private final Object asyncClient;
    private final MinioProperties minioProperties;

    public OssServiceImpl(MinioClient minioClient, MinioProperties minioProperties) throws Exception {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        // MinioClient 内部包装了 MinioAsyncClient（extends S3Base），
        // multipart 低层方法在 S3Base 中为 protected，需从内部 asyncClient 反射调用
        Field asyncField = MinioClient.class.getDeclaredField("asyncClient");
        asyncField.setAccessible(true);
        this.asyncClient = asyncField.get(minioClient);
    }

    /** 沿继承链查找方法（含 protected），找到后设置可访问。 */
    private java.lang.reflect.Method findMethod(String name, Class<?>... paramTypes) throws NoSuchMethodException {
        Class<?> cls = asyncClient.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Method m = cls.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignore) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    @Override
    public String initMultipartUpload(String bucket, String objectKey) {
        try {
            ensureBucketExists(bucket);

            // 反射调用 S3Base.createMultipartUpload(bucket, regionOverride, object, headers, extraQueryParams)
            Multimap<String, String> headers = ArrayListMultimap.create();
            Multimap<String, String> extraQueryParams = ArrayListMultimap.create();

            java.lang.reflect.Method method = findMethod(
                    "createMultipartUpload",
                    String.class, String.class, String.class, Multimap.class, Multimap.class);
            CreateMultipartUploadResponse response = (CreateMultipartUploadResponse) method.invoke(
                    asyncClient, bucket, null, objectKey, headers, extraQueryParams);

            String uploadId = response.result().uploadId();
            log.info("MinIO 分片上传初始化: bucket={}, objectKey={}, uploadId={}", bucket, objectKey, uploadId);
            return uploadId;
        } catch (Exception e) {
            log.error("MinIO 分片上传初始化失败: bucket={}, objectKey={}", bucket, objectKey, e);
            throw new RuntimeException("MinIO 分片上传初始化失败", e);
        }
    }

    @Override
    public String generatePresignedPutUrl(String bucket, String objectKey,
                                          String uploadId, int partNumber, int expiresSeconds) {
        try {
            // GetPresignedObjectUrlArgs 不支持额外查询参数，需手动拼接 S3 multipart 协议所需的 uploadId/partNumber
            String baseUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expiresSeconds, TimeUnit.SECONDS)
                            .build());

            String separator = baseUrl.contains("?") ? "&" : "?";
            return baseUrl
                    + separator
                    + "uploadId=" + URLEncoder.encode(uploadId, StandardCharsets.UTF_8)
                    + "&partNumber=" + partNumber;
        } catch (Exception e) {
            log.error("预签名 URL 生成失败: bucket={}, objectKey={}, partNumber={}",
                    bucket, objectKey, partNumber, e);
            throw new RuntimeException("预签名 URL 生成失败", e);
        }
    }

    @Override
    public String completeMultipartUpload(String bucket, String objectKey,
                                          String uploadId, Map<Integer, String> partEtags) {
        try {
            // 按 partNumber 的序构建 Part[] 列表
            Integer[] sortedPartNumbers = partEtags.keySet().stream()
                    .sorted()
                    .toArray(Integer[]::new);

            Part[] parts = new Part[sortedPartNumbers.length];
            for (int i = 0; i < sortedPartNumbers.length; i++) {
                parts[i] = new Part(sortedPartNumbers[i], partEtags.get(sortedPartNumbers[i]));
            }

            // 反射调用 S3Base.completeMultipartUpload(bucket, regionOverride, object, uploadId, Part[], headers, extraQueryParams)
            Multimap<String, String> headers = ArrayListMultimap.create();
            Multimap<String, String> extraQueryParams = ArrayListMultimap.create();

            java.lang.reflect.Method method = findMethod(
                    "completeMultipartUpload",
                    String.class, String.class, String.class, String.class,
                    Part[].class, Multimap.class, Multimap.class);
            ObjectWriteResponse response = (ObjectWriteResponse) method.invoke(
                    asyncClient, bucket, null, objectKey, uploadId, parts, headers, extraQueryParams);

            String fileUrl = buildFileUrl(bucket, objectKey);
            log.info("MinIO 分片合并完成: bucket={}, objectKey={}, partCount={}, etag={}, url={}",
                    bucket, objectKey, parts.length, response.etag(), fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("MinIO 分片合并失败: bucket={}, objectKey={}", bucket, objectKey, e);
            throw new RuntimeException("MinIO 分片合并失败", e);
        }
    }

    @Override
    public void abortMultipartUpload(String bucket, String objectKey, String uploadId) {
        try {
            // 反射调用 S3Base.abortMultipartUpload(bucket, regionOverride, object, uploadId, headers, extraQueryParams)
            Multimap<String, String> headers = ArrayListMultimap.create();
            Multimap<String, String> extraQueryParams = ArrayListMultimap.create();

            java.lang.reflect.Method method = findMethod(
                    "abortMultipartUpload",
                    String.class, String.class, String.class, String.class,
                    Multimap.class, Multimap.class);
            method.invoke(asyncClient, bucket, null, objectKey, uploadId, headers, extraQueryParams);

            log.info("MinIO 分片上传已取消: bucket={}, objectKey={}, uploadId={}",
                    bucket, objectKey, uploadId);
        } catch (Exception e) {
            log.error("MinIO 取消分片上传失败: bucket={}, objectKey={}", bucket, objectKey, e);
        }
    }

    private String buildFileUrl(String bucket, String objectKey) {
        String endpoint = minioProperties.getEndpoint();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + bucket + "/" + objectKey;
    }

    private void ensureBucketExists(String bucket) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket)
                    .build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucket)
                        .build());
                log.info("MinIO 桶不存在，已自动创建: bucket={}", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO 检查/创建桶失败: bucket={}", bucket, e);
            throw new RuntimeException("MinIO 桶操作失败", e);
        }
    }
}
