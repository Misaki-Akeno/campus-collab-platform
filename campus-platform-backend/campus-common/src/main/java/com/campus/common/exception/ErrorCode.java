package com.campus.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 全局成功
    SUCCESS(200, "success"),

    // 全局客户端错误
    PARAM_ERROR(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已失效"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),

    // 全局系统错误
    SYSTEM_ERROR(500, "系统内部错误"),

    // 用户域错误 1001-1010
    USER_NOT_FOUND(1001, "用户不存在"),
    USERNAME_EXISTS(1002, "用户名已存在"),
    PASSWORD_ERROR(1003, "密码错误"),

    // 社团域错误 1011-1020
    CLUB_NOT_FOUND(1011, "社团不存在"),
    CLUB_NAME_EXISTS(1012, "社团名称已存在"),
    ALREADY_MEMBER(1013, "已经是社团成员"),

    // IM域错误 1021-1030
    CONVERSATION_NOT_FOUND(1021, "会话不存在"),
    NOT_CONVERSATION_MEMBER(1022, "不是会话成员"),

    // 秒杀域错误 1031-1040
    ACTIVITY_NOT_FOUND(1031, "活动不存在"),
    STOCK_NOT_ENOUGH(1032, "名额不足"),
    ALREADY_BOOKED(1033, "已经报名过了"),
    ACTIVITY_NOT_STARTED(1034, "活动未开始"),
    ACTIVITY_ALREADY_ENDED(1035, "活动已结束"),

    // 文件域错误 1041-1050
    FILE_UPLOAD_FAILED(1041, "文件上传失败"),
    FILE_NOT_FOUND(1042, "文件不存在"),
    FILE_TOO_LARGE(1043, "文件过大");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
