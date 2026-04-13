package com.campus.club.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 成员记录状态枚举（对应 club_member.status 字段）
 */
@Getter
@AllArgsConstructor
public enum MemberStatus {

    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已拒绝");

    private final int code;
    private final String description;

    public static MemberStatus fromCode(int code) {
        for (MemberStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
