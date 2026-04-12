package com.campus.api.user;

import lombok.Data;

@Data
public class UserBasicDTO {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private Integer role;
}
