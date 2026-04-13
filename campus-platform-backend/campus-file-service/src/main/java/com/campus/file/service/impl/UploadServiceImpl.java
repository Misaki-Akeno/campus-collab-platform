package com.campus.file.service.impl;

import com.campus.common.constant.RedisKeyConstant;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.file.constant.UploadStatus;
import com.campus.file.entity.FileMeta;
import com.campus.file.mapper.FileMetaMapper;
import com.campus.file.service.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分片上传服务
 * <p>
 * 当前实现为骨架，MinIO SDK 调用（OssService）在 Phase 2 补充。
 * initUpload 已实现秒传和断点续传的判断逻辑，全新上传返回占位 URL。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final FileMetaMapper fileMetaMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Map<String, Object> initUpload(String fileMd5, String fileName,
                                          long fileSize, int chunkCount, Long uploaderId) {
        Map<String, Object> result = new HashMap<>();

        FileMeta existing = fileMetaMapper.selectById(fileMd5);

        if (existing != null && existing.getUploadStatus() == UploadStatus.COMPLETED
                && existing.getFileSize() == fileSize) {
            // 秒传：MD5 + fileSize 双重匹配，防 Hash Flooding 攻击
            log.info("文件秒传命中: fileId={}, fileName={}", fileMd5, fileName);
            result.put("type", "instant");
            result.put("fileUrl", existing.getFileUrl());
            return result;
        }

        if (existing != null && existing.getUploadStatus() == UploadStatus.UPLOADING) {
            // 断点续传：查询 Redis 已完成的分片
            String chunkKey = String.format(RedisKeyConstant.FILE_CHUNK, existing.getFileId());
            Map<Object, Object> uploadedChunks = redisTemplate.opsForHash().entries(chunkKey);
            log.info("断点续传: fileId={}, 已上传分片={}", fileMd5, uploadedChunks.size());
            result.put("type", "resume");
            result.put("uploadedParts", uploadedChunks.keySet());
            return result;
        }

        // 全新上传：创建 file_meta 记录
        FileMeta fileMeta = new FileMeta();
        fileMeta.setFileId(fileMd5);
        fileMeta.setFileName(fileName);
        fileMeta.setFileSize(fileSize);
        fileMeta.setChunkCount(chunkCount);
        fileMeta.setUploadStatus(UploadStatus.UPLOADING);
        fileMeta.setUploaderId(uploaderId);
        // fileUrl / fileType 合并后填充
        fileMeta.setFileUrl("");
        fileMetaMapper.insert(fileMeta);

        log.info("全新上传初始化: fileId={}, fileName={}, chunkCount={}", fileMd5, fileName, chunkCount);
        // TODO: 调用 OssService 获取 MinIO 预签名 URL 列表
        result.put("type", "new");
        result.put("uploadId", "TODO_MINIO_UPLOAD_ID");
        result.put("presignedUrls", List.of());
        return result;
    }

    @Override
    public void completeChunk(String uploadId, int partNumber, String etag) {
        String chunkKey = String.format(RedisKeyConstant.FILE_CHUNK, uploadId);
        redisTemplate.opsForHash().put(chunkKey, String.valueOf(partNumber), etag);
        log.info("分片完成上报: uploadId={}, partNumber={}, etag={}", uploadId, partNumber, etag);
    }

    @Override
    public String merge(String fileMd5, String uploadId) {
        FileMeta fileMeta = fileMetaMapper.selectById(fileMd5);
        if (fileMeta == null) {
            throw new BizException(ErrorCode.UPLOAD_NOT_FOUND);
        }
        // TODO: 调用 OssService 完成 MinIO completeMultipartUpload
        // 暂时更新状态为已完成
        FileMeta update = new FileMeta();
        update.setFileId(fileMd5);
        update.setUploadStatus(UploadStatus.COMPLETED);
        fileMetaMapper.updateById(update);

        // 清理 Redis 分片记录
        redisTemplate.delete(String.format(RedisKeyConstant.FILE_CHUNK, uploadId));
        log.info("文件合并完成: fileId={}, uploadId={}", fileMd5, uploadId);
        return fileMeta.getFileUrl();
    }

    @Override
    public FileMeta getFileMeta(String fileId) {
        FileMeta fileMeta = fileMetaMapper.selectById(fileId);
        if (fileMeta == null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        return fileMeta;
    }
}
