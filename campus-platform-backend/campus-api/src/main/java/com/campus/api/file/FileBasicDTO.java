package com.campus.api.file;

import lombok.Data;

@Data
public class FileBasicDTO {
    private String fileId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String fileUrl;
}
