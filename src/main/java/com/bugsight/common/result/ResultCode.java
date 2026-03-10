package com.bugsight.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    // 通用
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器内部错误"),

    // 用户
    USER_NOT_FOUND(1001, "用户不存在"),
    EMAIL_ALREADY_EXISTS(1002, "邮箱已被注册"),
    USERNAME_ALREADY_EXISTS(1003, "用户名已被使用"),
    WRONG_PASSWORD(1004, "密码错误"),
    ACCOUNT_DISABLED(1005, "账号已被禁用"),

    // 识别
    INFERENCE_FAILED(2001, "模型推理失败，请稍后重试"),
    IMAGE_FORMAT_INVALID(2002, "图片格式不支持，请上传 JPG/PNG/WEBP"),
    IMAGE_TOO_LARGE(2003, "图片大小不能超过 10MB"),

    // 收藏
    ALREADY_FAVORITED(3001, "已在收藏中"),
    NOT_FAVORITED(3002, "未收藏该昆虫"),

    // 社区
    POST_NOT_FOUND(4001, "帖子不存在"),
    COMMENT_NOT_FOUND(4002, "评论不存在"),
    NOT_POST_OWNER(4003, "非帖子作者，无权操作");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
