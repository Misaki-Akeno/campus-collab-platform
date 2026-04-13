package com.campus.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAnnouncementRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 256, message = "公告标题不超过256字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    private String content;

    private boolean isPinned = false;
}
