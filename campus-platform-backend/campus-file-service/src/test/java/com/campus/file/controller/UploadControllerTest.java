package com.campus.file.controller;

import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.common.exception.GlobalExceptionHandler;
import com.campus.file.entity.FileMeta;
import com.campus.file.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UploadController 单元测试 — Standalone MockMvc
 *
 * 注意：Controller 方法参数均为 @RequestParam（非 @RequestBody），
 * 且类级别无 @Validated，@NotBlank/@Positive 注解在 Standalone MockMvc
 * 下不会触发 ConstraintViolationException。
 * 缺少必填 @RequestParam 时 Spring 抛 MissingServletRequestParameterException，
 * 由 DefaultHandlerExceptionResolver 处理返回 HTTP 400。
 */
@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock
    private UploadService uploadService;

    @InjectMocks
    private UploadController uploadController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(uploadController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/upload/init
    // -------------------------------------------------------------------------

    @Test
    void initUpload_success() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "new");
        result.put("uploadId", "minio-upload-id-001");
        result.put("urls", List.of("https://minio/presigned/1", "https://minio/presigned/2"));
        when(uploadService.initUpload(
                eq("abc123md5"), eq("test.pdf"), eq(1024L), eq(2), eq(1L)))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/upload/init")
                        .header("X-User-Id", "1")
                        .param("fileMd5", "abc123md5")
                        .param("fileName", "test.pdf")
                        .param("fileSize", "1024")
                        .param("chunkCount", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.type").value("new"))
                .andExpect(jsonPath("$.data.uploadId").value("minio-upload-id-001"));
    }

    @Test
    void initUpload_instantTransfer() throws Exception {
        // 秒传场景：service 直接返回已有文件 URL
        Map<String, Object> result = new HashMap<>();
        result.put("type", "instant");
        result.put("fileUrl", "https://minio/files/abc123md5/test.pdf");
        when(uploadService.initUpload(
                eq("abc123md5"), eq("test.pdf"), eq(2048L), eq(1), eq(2L)))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/upload/init")
                        .header("X-User-Id", "2")
                        .param("fileMd5", "abc123md5")
                        .param("fileName", "test.pdf")
                        .param("fileSize", "2048"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.type").value("instant"))
                .andExpect(jsonPath("$.data.fileUrl").value("https://minio/files/abc123md5/test.pdf"));
    }

    @Test
    void initUpload_missingRequiredParam_returnsSystemError() throws Exception {
        // MissingServletRequestParameterException is caught by GlobalExceptionHandler's
        // catch-all handleException(Exception e) — returns HTTP 200 with SYSTEM_ERROR code (500).
        mockMvc.perform(post("/api/v1/upload/init")
                        .header("X-User-Id", "1")
                        .param("fileName", "test.pdf")
                        .param("fileSize", "1024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/upload/chunk/complete
    // -------------------------------------------------------------------------

    @Test
    void completeChunk_success() throws Exception {
        doNothing().when(uploadService).completeChunk(
                eq("minio-upload-id-001"), eq(1), eq("etag-part1"), eq(1L));

        mockMvc.perform(post("/api/v1/upload/chunk/complete")
                        .header("X-User-Id", "1")
                        .param("uploadId", "minio-upload-id-001")
                        .param("partNumber", "1")
                        .param("etag", "etag-part1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void completeChunk_ownerMismatch_returnsForbidden() throws Exception {
        // 非上传者操作 → service 抛 BizException(FORBIDDEN)
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(uploadService).completeChunk(
                        eq("minio-upload-id-001"), eq(1), eq("etag-part1"), eq(99L));

        mockMvc.perform(post("/api/v1/upload/chunk/complete")
                        .header("X-User-Id", "99")
                        .param("uploadId", "minio-upload-id-001")
                        .param("partNumber", "1")
                        .param("etag", "etag-part1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.msg").value(ErrorCode.FORBIDDEN.getMessage()));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/upload/merge
    // -------------------------------------------------------------------------

    @Test
    void merge_success_returnsFileUrl() throws Exception {
        when(uploadService.merge(
                eq("abc123md5"), eq("minio-upload-id-001"), eq(1L)))
                .thenReturn("https://minio/files/abc123md5/test.pdf");

        mockMvc.perform(post("/api/v1/upload/merge")
                        .header("X-User-Id", "1")
                        .param("fileMd5", "abc123md5")
                        .param("uploadId", "minio-upload-id-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileUrl").value("https://minio/files/abc123md5/test.pdf"));
    }

    @Test
    void merge_ownerMismatch_returnsForbidden() throws Exception {
        // 非上传者触发合并 → service 抛 BizException(FORBIDDEN)
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(uploadService).merge(
                        eq("abc123md5"), eq("minio-upload-id-001"), eq(99L));

        mockMvc.perform(post("/api/v1/upload/merge")
                        .header("X-User-Id", "99")
                        .param("fileMd5", "abc123md5")
                        .param("uploadId", "minio-upload-id-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.msg").value(ErrorCode.FORBIDDEN.getMessage()));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/upload/{fileId}
    // -------------------------------------------------------------------------

    @Test
    void getFileMeta_success() throws Exception {
        FileMeta meta = new FileMeta();
        meta.setFileId("abc123md5");
        meta.setFileName("test.pdf");
        meta.setFileSize(1024L);
        meta.setFileType("application/pdf");
        meta.setFileUrl("https://minio/files/abc123md5/test.pdf");
        meta.setChunkCount(1);
        meta.setUploadStatus(1);
        meta.setUploaderId(1L);
        when(uploadService.getFileMeta("abc123md5")).thenReturn(meta);

        mockMvc.perform(get("/api/v1/upload/abc123md5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value("abc123md5"))
                .andExpect(jsonPath("$.data.fileName").value("test.pdf"))
                .andExpect(jsonPath("$.data.uploadStatus").value(1));
    }

    @Test
    void getFileMeta_notFound_returnsFileNotFound() throws Exception {
        // 文件不存在 → service 抛 BizException(FILE_NOT_FOUND)
        when(uploadService.getFileMeta("nonexistent"))
                .thenThrow(new BizException(ErrorCode.FILE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/upload/nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FILE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.msg").value(ErrorCode.FILE_NOT_FOUND.getMessage()));
    }
}
