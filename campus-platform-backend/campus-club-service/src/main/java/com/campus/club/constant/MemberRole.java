package com.campus.club.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 社团成员角色枚举（对应 club_member.member_role 字段）
 */
@Getter
@AllArgsConstructor
public enum MemberRole {

    MEMBER(0, "普通成员"),
    VICE_LEADER(1, "副社长"),
    LEADER(2, "社长");

    private final int code;
    private final String description;

    /**
     * 是否具有管理权限（社长或副社长）
     */
    public boolean hasPermission() {
        return this == VICE_LEADER || this == LEADER;
    }

    /**
     * 根据 code 查找枚举，找不到返回 null
     */
    public static MemberRole fromCode(int code) {
        for (MemberRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return null;
    }

    /**
     * 是否可设置角色为该值（通过 updateMemberRole 接口允许的角色范围）
     */
    public static boolean isUpdatable(int code) {
        return code == MEMBER.code || code == VICE_LEADER.code;
    }
}
