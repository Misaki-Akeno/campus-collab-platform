package com.campus.file.service.impl;

import com.campus.common.constant.RedisKeyConstant;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.file.config.MinioProperties;
import com.campus.file.constant.UploadStatus;
import com.campus.file.entity.FileMeta;
import com.campus.file.mapper.FileMetaMapper;
import com.campus.file.service.OssService;
import com.campus.file.service.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadServiceImpl.class);
    private static final int PRESIGNED_URL_EXPIRE_SECONDS = 3600;
    private static final long UPLOAD_TMP_KEY_TTL_HOURS = 24;

    private final FileMetaMapper fileMetaMapper;
    private final StringRedisTemplate redisTemplate;
    private final OssService ossService;
    private final MinioProperties minioProperties;

    public UploadServiceImpl(FileMetaMapper fileMetaMapper, StringRedisTemplate redisTemplate,
                             OssService ossService, MinioProperties minioProperties) {
        this.fileMetaMapper = fileMetaMapper;
        this.redisTemplate = redisTemplate;
        this.ossService = ossService;
        this.minioProperties = minioProperties;
    }

    @Override
    public Map<String, Object> initUpload(String fileMd5, String fileName,
                                          long fileSize, int chunkCount, Long uploaderId) {
        Map<String, Object> result = new HashMap<>();

        FileMeta existing = fileMetaMapper.selectById(fileMd5);

        if (existing != null && existing.getUploadStatus() == UploadStatus.COMPLETED
                && existing.getFileSize() == fileSize) {
            log.info("文件秒传命中: fileId={}, fileName={}", fileMd5, fileName);
            result.put("type", "instant");
            result.put("fileUrl", existing.getFileUrl());
            return result;
        }

        if (existing != null && existing.getUploadStatus() == UploadStatus.UPLOADING) {
            String chunkKey = String.format(RedisKeyConstant.FILE_CHUNK, existing.getFileId());
            Map<Object, Object> uploadedChunks = redisTemplate.opsForHash().entries(chunkKey);
            redisTemplate.expire(chunkKey, UPLOAD_TMP_KEY_TTL_HOURS, TimeUnit.HOURS);
            log.info("断点续传: fileId={}, 已上传分片={}", fileMd5, uploadedChunks.size());
            result.put("type", "resume");
            result.put("uploadedParts", uploadedChunks.keySet());
            return result;
        }

        FileMeta fileMeta = new FileMeta();
        fileMeta.setFileId(fileMd5);
        fileMeta.setFileName(fileName);
        fileMeta.setFileSize(fileSize);
        fileMeta.setChunkCount(chunkCount);
        fileMeta.setUploadStatus(UploadStatus.UPLOADING);
        fileMeta.setUploaderId(uploaderId);
        fileMeta.setFileUrl("");
        fileMetaMapper.insert(fileMeta);

        log.info("全新上传初始化: fileId={}, fileName={}, chunkCount={}", fileMd5, fileName, chunkCount);
        String uploadId = buildUploadId(fileMd5, uploaderId);
        redisTemplate.opsForValue().set(buildUploadOwnerKey(uploadId), String.valueOf(uploaderId),
                UPLOAD_TMP_KEY_TTL_HOURS, TimeUnit.HOURS);

        String bucket = minioProperties.getBucketName();
        String objectKey = generateObjectKey(fileMd5, fileName);
        String minioUploadId = ossService.initMultipartUpload(bucket, objectKey);

        List<String> presignedUrls = new ArrayList<>();
        for (int i = 1; i <= chunkCount; i++) {
            String url = ossService.generatePresignedPutUrl(bucket, objectKey,
                    minioUploadId, i, PRESIGNED_URL_EXPIRE_SECONDS);
            presignedUrls.add(url);
        }

        redisTemplate.opsForValue().set(buildUploadObjectKey(uploadId), objectKey,
                UPLOAD_TMP_KEY_TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForValue().set(buildUploadMinioUploadId(uploadId), minioUploadId,
                UPLOAD_TMP_KEY_TTL_HOURS, TimeUnit.HOURS);

        result.put("type", "new");
        result.put("uploadId", uploadId);
        result.put("presignedUrls", presignedUrls);
        result.put("minioUploadId", minioUploadId);
        return result;
    }

    @Override
    public void completeChunk(String uploadId, int partNumber, String etag, Long uploaderId) {
        validateUploadOwner(uploadId, uploaderId);
        String chunkKey = String.format(RedisKeyConstant.FILE_CHUNK, uploadId);
        redisTemplate.opsForHash().put(chunkKey, String.valueOf(partNumber), etag);
        redisTemplate.expire(chunkKey, UPLOAD_TMP_KEY_TTL_HOURS, TimeUnit.HOURS);
        log.info("分片完成上报: uploadId={}, partNumber={}, etag={}", uploadId, partNumber, etag);
    }

    @Override
    public String merge(String fileMd5, String uploadId, Long uploaderId) {
        validateUploadOwner(uploadId, uploaderId);

        String embeddedFileMd5 = extractFileMd5FromUploadId(uploadId);
        if (!Objects.equals(embeddedFileMd5, fileMd5)) {
            log.warn("上传任务 MD5 不匹配: uploadId={}, expectedMd5={}, actualMd5={}",
                    uploadId, embeddedFileMd5, fileMd5);
            throw new BizException(ErrorCode.PARAM_ERROR, "上传任务与文件 MD5 不匹配");
        }

        FileMeta fileMeta = fileMetaMapper.selectById(fileMd5);
        if (fileMeta == null) {
            throw new BizException(ErrorCode.UPLOAD_NOT_FOUND);
        }
        if (!Objects.equals(fileMeta.getUploaderId(), uploaderId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该上传任务");
        }
        if (fileMeta.getUploadStatus() != UploadStatus.UPLOADING) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAIL,
                    "当前上传状态不允许合并，当前状态=" + fileMeta.getUploadStatus());
        }

        validateChunkCompleteness(uploadId, fileMeta.getChunkCount());

        Map<Integer, String> partEtags = collectChunkEtags(uploadId);

        String bucket = minioProperties.getBucketName();
        String objectKey = resolveObjectKey(uploadId);
        String minioUploadId = resolveMinioUploadId(uploadId);

        String fileUrl = ossService.completeMultipartUpload(bucket, objectKey,
                minioUploadId, partEtags);

        FileMeta update = new FileMeta();
        update.setFileId(fileMd5);
        update.setUploadStatus(UploadStatus.COMPLETED);
        update.setFileUrl(fileUrl);
        fileMetaMapper.updateById(update);

        cleanup(uploadId);

        log.info("文件合并完成: fileId={}, fileUrl={}", fileMd5, fileUrl);
        return fileUrl;
    }

    @Override
    public FileMeta getFileMeta(String fileId) {
        FileMeta fileMeta = fileMetaMapper.selectById(fileId);
        if (fileMeta == null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        return fileMeta;
    }

    private void validateUploadOwner(String uploadId, Long uploaderId) {
        String uploadOwner = redisTemplate.opsForValue().get(buildUploadOwnerKey(uploadId));
        if (uploadOwner == null || !uploadOwner.equals(String.valueOf(uploaderId))) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该上传任务");
        }
    }

    private void validateChunkCompleteness(String uploadId, int expectedCount) {
        String chunkKey = String.format(RedisKeyConstant.FILE_CHUNK, uploadId);
        Long actualCount = redisTemplate.opsForHash().size(chunkKey);
        if (actualCount != expectedCount) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAIL,
                    String.format("分片未全部上传完成: 期望=%d, 实际=%d", expectedCount, actualCount));
        }
    }

    private Map<Integer, String> collectChunkEtags(String uploadId) {
        String chunkKey = String.format(RedisKeyConstant.FILE_CHUNK, uploadId);
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(chunkKey);
        Map<Integer, String> partEtags = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            partEtags.put(Integer.parseInt(String.valueOf(entry.getKey())),
                    String.valueOf(entry.getValue()));
        }
        return partEtags;
    }

    private String extractFileMd5FromUploadId(String uploadId) {
        int colonIndex = uploadId.indexOf(':');
        if (colonIndex <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上传任务格式错误");
        }
        return uploadId.substring(0, colonIndex);
    }

    private String buildUploadOwnerKey(String uploadId) {
        return String.format(RedisKeyConstant.FILE_UPLOAD_STATUS, uploadId);
    }

    private String buildUploadId(String fileMd5, Long uploaderId) {
        return fileMd5 + ":" + uploaderId;
    }

    private String buildUploadObjectKey(String uploadId) {
        return "file:upload:object:" + uploadId;
    }

    private String buildUploadMinioUploadId(String uploadId) {
        return "file:upload:minio:" + uploadId;
    }

    private String generateObjectKey(String fileMd5, String fileName) {
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            ext = fileName.substring(dotIndex);
        }
        return "uploads/" + fileMd5 + ext;
    }

    private String resolveObjectKey(String uploadId) {
        String key = redisTemplate.opsForValue().get(buildUploadObjectKey(uploadId));
        if (key == null) {
            throw new BizException(ErrorCode.UPLOAD_NOT_FOUND, "上传任务对象键不存在");
        }
        return key;
    }

    private String resolveMinioUploadId(String uploadId) {
        String minioUploadId = redisTemplate.opsForValue()
                .get(buildUploadMinioUploadId(uploadId));
        if (minioUploadId == null) {
            throw new BizException(ErrorCode.UPLOAD_NOT_FOUND, "MinIO uploadId 不存在");
        }
        return minioUploadId;
    }

    private void cleanup(String uploadId) {
        redisTemplate.delete(String.format(RedisKeyConstant.FILE_CHUNK, uploadId));
        redisTemplate.delete(buildUploadOwnerKey(uploadId));
        redisTemplate.delete(buildUploadObjectKey(uploadId));
        redisTemplate.delete(buildUploadMinioUploadId(uploadId));
    }
}
