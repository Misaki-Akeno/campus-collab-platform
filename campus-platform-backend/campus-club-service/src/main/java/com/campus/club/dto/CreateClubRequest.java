package com.campus.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateClubRequest {

    @NotBlank(message = "社团名称不能为空")
    @Size(max = 128, message = "社团名称不超过128字符")
    private String name;

    @Size(max = 2000, message = "社团简介不超过2000字符")
    private String description;

    @Size(max = 32, message = "分类不超过32字符")
    private String category;
}
