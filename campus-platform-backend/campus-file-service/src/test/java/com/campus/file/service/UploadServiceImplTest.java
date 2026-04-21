package com.campus.file.service;

import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.file.config.MinioProperties;
import com.campus.file.constant.UploadStatus;
import com.campus.file.entity.FileMeta;
import com.campus.file.mapper.FileMetaMapper;
import com.campus.file.service.impl.UploadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UploadServiceImpl 单元测试。
 *
 * <p>测试策略：
 * - 全部使用 Mockito 纯 Mock，不启动 Spring 容器
 * - UploadServiceImpl 通过构造器注入，@InjectMocks 自动完成
 * - Redis 链式调用需分别 Mock opsForValue() 和 opsForHash()
 * - UploadStatus 为 int 常量（0=UPLOADING / 1=COMPLETED），FileMeta.uploadStatus 是 Integer
 * - BizException 使用 .getCode() 断言，不是 .getErrorCode()
 */
@ExtendWith(MockitoExtension.class)
class UploadServiceImplTest {

    @Mock private FileMetaMapper fileMetaMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private OssService ossService;
    @Mock private MinioProperties minioProperties;

    // Redis 操作链 mock
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private HashOperations<String, Object, Object> hashOps;

    @InjectMocks
    private UploadServiceImpl uploadService;

    private static final String FILE_MD5 = "abc123md5";
    private static final String FILE_NAME = "test.pdf";
    private static final long FILE_SIZE = 10_485_760L; // 10 MB
    private static final int CHUNK_COUNT = 2;
    private static final Long UPLOADER_ID = 42L;
    private static final String BUCKET = "test-bucket";

    // uploadId 格式：fileMd5 + ":" + uploaderId
    private static final String UPLOAD_ID = FILE_MD5 + ":" + UPLOADER_ID;

    // Redis key 格式
    private static final String OWNER_KEY = "file:upload:" + UPLOAD_ID;
    private static final String CHUNK_KEY  = "file:chunk:" + UPLOAD_ID;
    private static final String OBJECT_KEY_REDIS = "file:upload:object:" + UPLOAD_ID;
    private static final String MINIO_ID_REDIS   = "file:upload:minio:"  + UPLOAD_ID;

    // Resume path: UploadServiceImpl uses existing.getFileId() (= FILE_MD5) as the chunk key,
    // not uploadId. Compute dynamically to stay bound to the actual FileMeta object.
    private static String resumeChunkKey(FileMeta existing) {
        return "file:chunk:" + existing.getFileId();
    }

    @BeforeEach
    void setUp() {
        // lenient: not every test exercises Redis or MinIO, but most do
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(minioProperties.getBucketName()).thenReturn(BUCKET);
    }

    // =========================================================================
    // initUpload()
    // =========================================================================

    /**
     * 秒传命中：DB 中已存在 status=COMPLETED 且 fileSize 相同的记录，
     * 直接返回 fileUrl，不应调用 ossService.initMultipartUpload。
     */
    @Test
    void initUpload_instantTransfer_fileAlreadyExists() {
        FileMeta existing = buildFileMeta(FILE_MD5, UploadStatus.COMPLETED, FILE_SIZE, UPLOADER_ID);
        existing.setFileUrl("https://minio.example.com/uploads/abc123.pdf");

        when(fileMetaMapper.selectById(FILE_MD5)).thenReturn(existing);

        Map<String, Object> result = uploadService.initUpload(
                FILE_MD5, FILE_NAME, FILE_SIZE, CHUNK_COUNT, UPLOADER_ID);

        assertEquals("instant", result.get("type"));
        assertEquals("https://minio.example.com/uploads/abc123.pdf", result.get("fileUrl"));
        verify(ossService, never()).initMultipartUpload(anyString(), anyString());
    }

    /**
     * 断点续传：DB 中已存在 status=UPLOADING 的记录，
     * 查询 Redis 已上传分片并返回，不创建新上传任务。
     */
    @Test
    void initUpload_resumeUpload_uploadingExists() {
        FileMeta existing = buildFileMeta(FILE_MD5, UploadStatus.UPLOADING, FILE_SIZE, UPLOADER_ID);
        when(fileMetaMapper.selectById(FILE_MD5)).thenReturn(existing);

        String chunkKey = resumeChunkKey(existing);
        Map<Object, Object> alreadyUploaded = new HashMap<>();
        alreadyUploaded.put("1", "etag-part1");
        when(hashOps.entries(eq(chunkKey))).thenReturn(alreadyUploaded);
        when(redisTemplate.expire(eq(chunkKey), anyLong(), any(TimeUnit.class))).thenReturn(true);

        Map<String, Object> result = uploadService.initUpload(
                FILE_MD5, FILE_NAME, FILE_SIZE, CHUNK_COUNT, UPLOADER_ID);

        assertEquals("resume", result.get("type"));
        assertNotNull(result.get("uploadedParts"));
        verify(ossService, never()).initMultipartUpload(anyString(), anyString());
    }

    /**
     * 全新上传：DB 中无此 MD5 记录，
     * 应调用 ossService.initMultipartUpload 并返回预签名 URL 列表。
     */
    @Test
    void initUpload_newUpload_noExistingFile() {
        when(fileMetaMapper.selectById(FILE_MD5)).thenReturn(null);
        when(ossService.initMultipartUpload(BUCKET, "uploads/" + FILE_MD5 + ".pdf"))
                .thenReturn("minio-upload-id-001");
        when(ossService.generatePresignedPutUrl(eq(BUCKET), anyString(), eq("minio-upload-id-001"),
                anyInt(), anyInt()))
                .thenReturn("https://presigned.url/part");
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        Map<String, Object> result = uploadService.initUpload(
                FILE_MD5, FILE_NAME, FILE_SIZE, CHUNK_COUNT, UPLOADER_ID);

        assertEquals("new", result.get("type"));
        assertNotNull(result.get("uploadId"));
        assertEquals("minio-upload-id-001", result.get("minioUploadId"));

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) result.get("presignedUrls");
        assertEquals(CHUNK_COUNT, urls.size());

        verify(ossService, times(1)).initMultipartUpload(BUCKET, "uploads/" + FILE_MD5 + ".pdf");
        verify(ossService, times(CHUNK_COUNT)).generatePresignedPutUrl(
                eq(BUCKET), anyString(), eq("minio-upload-id-001"), anyInt(), anyInt());
    }

    // =========================================================================
    // completeChunk()
    // =========================================================================

    /**
     * 正常上报分片：uploaderId 匹配，分片信息写入 Redis Hash。
     */
    @Test
    void completeChunk_success() {
        when(valueOps.get(OWNER_KEY)).thenReturn(String.valueOf(UPLOADER_ID));
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        assertDoesNotThrow(() ->
                uploadService.completeChunk(UPLOAD_ID, 1, "etag-abc", UPLOADER_ID));

        verify(hashOps).put(CHUNK_KEY, "1", "etag-abc");
        verify(redisTemplate).expire(eq(CHUNK_KEY), anyLong(), any(TimeUnit.class));
    }

    /**
     * 上传者不匹配：Redis 中记录的 owner 与传入 uploaderId 不同，抛出 FORBIDDEN。
     */
    @Test
    void completeChunk_ownerMismatch_throwsBizException() {
        when(valueOps.get(OWNER_KEY)).thenReturn("999"); // 不是 UPLOADER_ID=42

        BizException ex = assertThrows(BizException.class, () ->
                uploadService.completeChunk(UPLOAD_ID, 1, "etag-abc", UPLOADER_ID));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(hashOps, never()).put(anyString(), anyString(), anyString());
    }

    /**
     * owner key 不存在（Redis 无记录），同样抛出 FORBIDDEN。
     */
    @Test
    void completeChunk_uploadNotFound_throwsBizException() {
        when(valueOps.get(OWNER_KEY)).thenReturn(null); // key 不存在

        BizException ex = assertThrows(BizException.class, () ->
                uploadService.completeChunk(UPLOAD_ID, 1, "etag-abc", UPLOADER_ID));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    // =========================================================================
    // merge()
    // =========================================================================

    /**
     * 合并成功：所有校验通过，ossService.completeMultipartUpload 被调用 1 次，返回 fileUrl。
     */
    @Test
    void merge_success_returnsFileUrl() {
        // validateUploadOwner 通过
        when(valueOps.get(OWNER_KEY)).thenReturn(String.valueOf(UPLOADER_ID));

        // DB 查询返回 UPLOADING 状态
        FileMeta fileMeta = buildFileMeta(FILE_MD5, UploadStatus.UPLOADING, FILE_SIZE, UPLOADER_ID);
        when(fileMetaMapper.selectById(FILE_MD5)).thenReturn(fileMeta);

        // 分片数量校验通过
        when(hashOps.size(CHUNK_KEY)).thenReturn((long) CHUNK_COUNT);

        // 收集 etag
        Map<Object, Object> chunks = new HashMap<>();
        chunks.put("1", "etag-1");
        chunks.put("2", "etag-2");
        when(hashOps.entries(CHUNK_KEY)).thenReturn(chunks);

        // resolveObjectKey / resolveMinioUploadId
        when(valueOps.get(OBJECT_KEY_REDIS)).thenReturn("uploads/" + FILE_MD5 + ".pdf");
        when(valueOps.get(MINIO_ID_REDIS)).thenReturn("minio-upload-id-001");

        // OSS 合并返回 URL
        when(ossService.completeMultipartUpload(eq(BUCKET), anyString(), anyString(), any()))
                .thenReturn("https://minio.example.com/uploads/abc123.pdf");

        // cleanup 的 delete 调用
        when(redisTemplate.delete(anyString())).thenReturn(true);

        String fileUrl = uploadService.merge(FILE_MD5, UPLOAD_ID, UPLOADER_ID);

        assertEquals("https://minio.example.com/uploads/abc123.pdf", fileUrl);
        verify(ossService, times(1))
                .completeMultipartUpload(eq(BUCKET), anyString(), anyString(), any());
        verify(fileMetaMapper).updateById(any(FileMeta.class));
    }

    /**
     * uploadId 中的 fileMd5 与入参 fileMd5 不匹配，抛出 PARAM_ERROR。
     * （UPLOAD_ID = FILE_MD5 + ":" + UPLOADER_ID，传入不同的 md5）
     */
    @Test
    void merge_md5Mismatch_throwsBizException() {
        // validateUploadOwner 通过（需先通过 owner 校验才能到达 md5 校验）
        String wrongMd5UploadId = "wrongMd5:" + UPLOADER_ID;
        String wrongOwnerKey = "file:upload:" + wrongMd5UploadId;
        when(valueOps.get(wrongOwnerKey)).thenReturn(String.valueOf(UPLOADER_ID));

        // 传入的 fileMd5 与 uploadId 嵌入的不同
        BizException ex = assertThrows(BizException.class, () ->
                uploadService.merge(FILE_MD5, wrongMd5UploadId, UPLOADER_ID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(ossService, never()).completeMultipartUpload(anyString(), anyString(), anyString(), any());
    }

    /**
     * 上传者不匹配：Redis owner 与入参 uploaderId 不同，抛出 FORBIDDEN，OSS 从不被调用。
     */
    @Test
    void merge_ownerMismatch_throwsBizException() {
        when(valueOps.get(OWNER_KEY)).thenReturn("999"); // 不是 UPLOADER_ID

        BizException ex = assertThrows(BizException.class, () ->
                uploadService.merge(FILE_MD5, UPLOAD_ID, UPLOADER_ID));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(ossService, never()).completeMultipartUpload(anyString(), anyString(), anyString(), any());
    }

    /**
     * 分片未全部上传：Redis Hash size 小于 chunkCount，抛出 FILE_UPLOAD_FAIL。
     */
    @Test
    void merge_incompleteChunks_throwsBizException() {
        when(valueOps.get(OWNER_KEY)).thenReturn(String.valueOf(UPLOADER_ID));

        FileMeta fileMeta = buildFileMeta(FILE_MD5, UploadStatus.UPLOADING, FILE_SIZE, UPLOADER_ID);
        when(fileMetaMapper.selectById(FILE_MD5)).thenReturn(fileMeta);

        // 只上传了 1 片，但 chunkCount=2
        when(hashOps.size(CHUNK_KEY)).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () ->
                uploadService.merge(FILE_MD5, UPLOAD_ID, UPLOADER_ID));

        assertEquals(ErrorCode.FILE_UPLOAD_FAIL.getCode(), ex.getCode());
        verify(ossService, never()).completeMultipartUpload(anyString(), anyString(), anyString(), any());
    }

    /**
     * status=COMPLETED 时不可再次 merge，抛出 FILE_UPLOAD_FAIL。
     */
    @Test
    void merge_statusNotUploading_throwsBizException() {
        when(valueOps.get(OWNER_KEY)).thenReturn(String.valueOf(UPLOADER_ID));

        // 已是 COMPLETED 状态
        FileMeta fileMeta = buildFileMeta(FILE_MD5, UploadStatus.COMPLETED, FILE_SIZE, UPLOADER_ID);
        when(fileMetaMapper.selectById(FILE_MD5)).thenReturn(fileMeta);

        BizException ex = assertThrows(BizException.class, () ->
                uploadService.merge(FILE_MD5, UPLOAD_ID, UPLOADER_ID));

        assertEquals(ErrorCode.FILE_UPLOAD_FAIL.getCode(), ex.getCode());
        verify(ossService, never()).completeMultipartUpload(anyString(), anyString(), anyString(), any());
    }

    // =========================================================================
    // getFileMeta()
    // =========================================================================

    /**
     * 正常查询：fileId 存在，返回 FileMeta 对象。
     */
    @Test
    void getFileMeta_success() {
        FileMeta fileMeta = buildFileMeta(FILE_MD5, UploadStatus.COMPLETED, FILE_SIZE, UPLOADER_ID);
        fileMeta.setFileUrl("https://minio.example.com/uploads/abc123.pdf");
        when(fileMetaMapper.selectById(FILE_MD5)).thenReturn(fileMeta);

        FileMeta result = uploadService.getFileMeta(FILE_MD5);

        assertNotNull(result);
        assertEquals(FILE_MD5, result.getFileId());
        assertEquals("https://minio.example.com/uploads/abc123.pdf", result.getFileUrl());
        assertEquals(UploadStatus.COMPLETED, result.getUploadStatus());
    }

    /**
     * 文件不存在：selectById 返回 null，抛出 FILE_NOT_FOUND。
     */
    @Test
    void getFileMeta_notFound_throwsBizException() {
        when(fileMetaMapper.selectById("nonexistent-md5")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () ->
                uploadService.getFileMeta("nonexistent-md5"));

        assertEquals(ErrorCode.FILE_NOT_FOUND.getCode(), ex.getCode());
    }

    // =========================================================================
    // helper
    // =========================================================================

    private FileMeta buildFileMeta(String fileId, int uploadStatus, long fileSize, Long uploaderId) {
        FileMeta meta = new FileMeta();
        meta.setFileId(fileId);
        meta.setFileName(FILE_NAME);
        meta.setFileSize(fileSize);
        meta.setChunkCount(CHUNK_COUNT);
        meta.setUploadStatus(uploadStatus);
        meta.setUploaderId(uploaderId);
        meta.setFileUrl("");
        return meta;
    }
}
