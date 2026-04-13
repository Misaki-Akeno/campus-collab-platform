package com.campus.user.dto;

import lombok.Data;

@Data
public class MeResponse {

    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private Integer role;
    private String avatarUrl;
}
