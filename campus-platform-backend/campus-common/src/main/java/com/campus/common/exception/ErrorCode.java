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
    TOO_MANY_REQUESTS(429, "请求频率超限"),

    // 全局系统错误
    SYSTEM_ERROR(500, "系统内部错误"),

    // 用户域错误 1001-1010
    USER_NOT_FOUND(1001, "用户不存在"),
    USERNAME_EXISTS(1002, "用户名已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),

    // 社团域错误 1011-1020
    CLUB_NOT_FOUND(1011, "社团不存在"),
    CLUB_NAME_EXISTS(1012, "社团名称已存在"),
    ALREADY_MEMBER(1013, "已经是社团成员"),
    NOT_CLUB_MEMBER(1014, "不是社团成员"),
    PERMISSION_DENIED(1015, "社团权限不足"),

    // IM域错误 1021-1030
    CONVERSATION_NOT_FOUND(1021, "会话不存在"),
    NOT_CONVERSATION_MEMBER(1022, "不是会话成员"),
    MESSAGE_NOT_FOUND(1023, "消息不存在"),
    RECALL_TIMEOUT(1024, "消息撤回超时(2分钟内可撤回)"),

    // 文件域错误 1041-1050
    FILE_NOT_FOUND(1041, "文件不存在"),
    FILE_TOO_LARGE(1042, "文件过大"),
    UPLOAD_NOT_FOUND(1043, "上传任务不存在"),
    FILE_UPLOAD_FAIL(1044, "文件上传失败"),

    // 秒杀域错误 — 与白皮书码段对齐
    STOCK_EMPTY(5001, "活动名额已满"),
    DUPLICATE_BOOK(5002, "您已报名该活动，请勿重复操作"),
    ACTIVITY_NOT_START(5003, "活动报名未开始"),
    ACTIVITY_ENDED(5004, "活动报名已结束"),
    ACTIVITY_CANCELLED(5005, "活动已取消");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
