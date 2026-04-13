package com.campus.file.service;

import com.campus.file.entity.FileMeta;

import java.util.List;
import java.util.Map;

public interface UploadService {

    /**
     * 初始化上传（含秒传判断）
     * @param fileMd5   文件 MD5
     * @param fileName  原始文件名
     * @param fileSize  文件大小（字节）
     * @param chunkCount 分片总数
     * @param uploaderId 上传者 ID
     * @return 响应结果，包含 type（instant/resume/new）及预签名 URL 列表
     */
    Map<String, Object> initUpload(String fileMd5, String fileName,
                                   long fileSize, int chunkCount, Long uploaderId);

    /**
     * 上报分片完成
     * @param uploadId   MinIO multipart uploadId
     * @param partNumber 分片序号（从 1 开始）
     * @param etag       分片 ETag
     */
    void completeChunk(String uploadId, int partNumber, String etag);

    /**
     * 合并分片，完成上传
     * @param fileMd5  文件 MD5（fileId）
     * @param uploadId MinIO multipart uploadId
     * @return 文件访问 URL
     */
    String merge(String fileMd5, String uploadId);

    /** 根据 fileId（MD5）获取文件元数据 */
    FileMeta getFileMeta(String fileId);
}
