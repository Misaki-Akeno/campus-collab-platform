package com.campus.file.controller;

import com.campus.common.result.Result;
import com.campus.file.entity.FileMeta;
import com.campus.file.service.UploadService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /**
     * POST /api/v1/upload/init — 初始化上传（含秒传判断）
     */
    @PostMapping("/init")
    public Result<Map<String, Object>> init(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam @NotBlank String fileMd5,
            @RequestParam @NotBlank String fileName,
            @RequestParam @Positive long fileSize,
            @RequestParam(defaultValue = "1") @Positive int chunkCount) {
        return Result.ok(uploadService.initUpload(fileMd5, fileName, fileSize, chunkCount, userId));
    }

    /**
     * POST /api/v1/upload/chunk/complete — 上报分片完成
     */
    @PostMapping("/chunk/complete")
    public Result<Void> completeChunk(
            @RequestParam @NotBlank String uploadId,
            @RequestParam @Positive int partNumber,
            @RequestParam @NotBlank String etag) {
        uploadService.completeChunk(uploadId, partNumber, etag);
        return Result.ok();
    }

    /**
     * POST /api/v1/upload/merge — 合并分片
     */
    @PostMapping("/merge")
    public Result<Map<String, Object>> merge(
            @RequestParam @NotBlank String fileMd5,
            @RequestParam @NotBlank String uploadId) {
        String fileUrl = uploadService.merge(fileMd5, uploadId);
        return Result.ok(Map.of("fileUrl", fileUrl));
    }

    /**
     * GET /api/v1/upload/{fileId} — 获取文件元数据
     */
    @GetMapping("/{fileId}")
    public Result<FileMeta> getFileMeta(@PathVariable String fileId) {
        return Result.ok(uploadService.getFileMeta(fileId));
    }
}
