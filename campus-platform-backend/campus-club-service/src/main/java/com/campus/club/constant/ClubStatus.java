package com.campus.club.constant;

/**
 * 社团状态常量（对应 club.status 字段）
 * DDL COMMENT: 0-待审核 1-正常 2-已解散 3-审核拒绝
 */
public final class ClubStatus {
    private ClubStatus() {}

    public static final int PENDING   = 0; // 待审核
    public static final int ACTIVE    = 1; // 正常
    public static final int DISSOLVED = 2; // 已解散
    public static final int REJECTED  = 3; // 审核拒绝
}
