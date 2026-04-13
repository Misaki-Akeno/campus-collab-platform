package com.campus.file.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据表（主键为 MD5，非雪花ID）。
 * file_meta 表无 is_deleted 字段，不使用逻辑删除，也无需继承 BaseEntity。
 */
@Data
@TableName("file_meta")
public class FileMeta {
    @TableId
    private String fileId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String fileUrl;
    private Integer chunkCount;
    /** 0-上传中 1-已完成 */
    private Integer uploadStatus;
    private Long uploaderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
