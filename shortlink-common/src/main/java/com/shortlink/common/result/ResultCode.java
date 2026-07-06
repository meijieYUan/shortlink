package com.shortlink.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    GONE(410, "资源已失效"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务错误码 1000+
    SHORT_CODE_NOT_FOUND(1001, "短链接不存在"),
    SHORT_CODE_EXPIRED(1002, "短链接已过期"),
    SHORT_CODE_CONFLICT(1003, "自定义短码已被占用"),
    URL_INVALID(1004, "URL 格式不合法"),
    RATE_LIMIT_EXCEEDED(1005, "超出调用频率限制"),
    QUOTA_EXCEEDED(1006, "超出配额限制"),
    GENERATE_FAILED(1007, "短链接生成失败"),
    ;

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
