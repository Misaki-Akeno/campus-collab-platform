package com.campus.user.dto;

import lombok.Data;

import java.util.List;

@Data
public class MeResponse {

    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private Integer role;
    private String avatarUrl;

    /** 用户参与的社团列表（对应 API.md A4），来自 club-service Feign 调用 */
    private List<ClubInfo> clubs;

    @Data
    public static class ClubInfo {
        private Long clubId;
        private String clubName;
        /** 社团内角色：0-普通成员 1-副社长 2-社长 */
        private Integer memberRole;
    }
}
