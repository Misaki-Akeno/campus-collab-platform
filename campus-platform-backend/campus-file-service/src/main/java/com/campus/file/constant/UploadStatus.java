package com.campus.file.constant;

/**
 * 文件上传状态常量（对应 file_meta.upload_status 字段）
 */
public final class UploadStatus {
    private UploadStatus() {}

    public static final int UPLOADING = 0; // 上传中
    public static final int COMPLETED = 1; // 已完成
}
