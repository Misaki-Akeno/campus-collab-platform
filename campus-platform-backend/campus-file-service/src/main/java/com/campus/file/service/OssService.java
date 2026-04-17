package com.campus.file.service;

import java.util.List;
import java.util.Map;

/**
 * MinIO/OSS 分片操作封装。
 */
public interface OssService {

    /**
     * 初始化 MinIO 分片上传
     * @param bucket     桶名
     * @param objectKey  对象键
     * @return MinIO multipart uploadId
     */
    String initMultipartUpload(String bucket, String objectKey);

    /**
     * 为指定分片生成预签名 PUT URL
     * @param bucket     桶名
     * @param objectKey  对象键
     * @param uploadId   MinIO multipart uploadId
     * @param partNumber 分片序号（从 1 开始）
     * @param expiresSeconds 预签名 URL 有效期（秒）
     * @return 预签名 URL
     */
    String generatePresignedPutUrl(String bucket, String objectKey,
                                   String uploadId, int partNumber, int expiresSeconds);

    /**
     * 完成分片上传（触发 MinIO merge）
     * @param bucket     桶名
     * @param objectKey  对象键
     * @param uploadId   MinIO multipart uploadId
     * @param partEtags  partNumber -> ETag 映射
     * @return 文件的最终访问 URL
     */
    String completeMultipartUpload(String bucket, String objectKey,
                                   String uploadId, Map<Integer, String> partEtags);

    /**
     * 取消分片上传（清理 MinIO 残留分片）
     * @param bucket     桶名
     * @param objectKey  对象键
     * @param uploadId   MinIO multipart uploadId
     */
    void abortMultipartUpload(String bucket, String objectKey, String uploadId);
}
