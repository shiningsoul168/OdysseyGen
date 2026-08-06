package com.odysseygen.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数有误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ====== 业务错误码（1000-1999） ======
    USER_EXIST(1001, "用户名已存在"),
    USER_NOT_EXIST(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    TOKEN_INVALID(1004, "Token 无效或已过期"),

    PLAN_NOT_EXIST(2001, "规划不存在"),
    PROFILE_NOT_EXIST(2002, "画像不存在"),
    AI_GENERATE_ERROR(3001, "AI 生成失败，请稍后重试"),
    AI_CIRCUIT_BREAKER_OPEN(3002, "AI 服务繁忙，请稍后重试");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
