package com.campus.api.club;

import lombok.Data;

@Data
public class ClubBasicDTO {
    private Long id;
    private String name;
    private String logoUrl;
    private Integer status;
}
