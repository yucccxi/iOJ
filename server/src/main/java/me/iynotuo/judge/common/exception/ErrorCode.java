package me.iynotuo.judge.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Business error codes. The HTTP status gives a quick signal; code lets the client show a precise message.
 */
public enum ErrorCode {

    BAD_REQUEST(1000, "请求参数错误", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(1001, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1002, "未登录或登录已失效", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1003, "没有权限执行该操作", HttpStatus.FORBIDDEN),
    NOT_FOUND(1004, "资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT(1005, "资源已存在或状态冲突", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(1006, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),

    USER_NOT_FOUND(2001, "用户不存在", HttpStatus.NOT_FOUND),
    USERNAME_TAKEN(2002, "用户名已被占用", HttpStatus.CONFLICT),
    EMAIL_TAKEN(2003, "邮箱已被注册", HttpStatus.CONFLICT),
    USER_DISABLED(2004, "账号已被禁用", HttpStatus.FORBIDDEN),
    BAD_CREDENTIALS(2005, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    OLD_PASSWORD_MISMATCH(2006, "原密码不正确", HttpStatus.BAD_REQUEST),

    PROBLEM_NOT_FOUND(3001, "题目不存在", HttpStatus.NOT_FOUND),
    PROBLEM_NOT_PUBLISHED(3002, "题目尚未发布", HttpStatus.FORBIDDEN),
    PROBLEM_NO_TESTCASE(3003, "题目至少需要一个测试点才能发布", HttpStatus.BAD_REQUEST),
    TESTCASE_NOT_FOUND(3004, "测试点不存在", HttpStatus.NOT_FOUND),
    TESTCASE_LIMIT_EXCEEDED(3005, "测试点数量超出上限", HttpStatus.BAD_REQUEST),

    SUBMISSION_NOT_FOUND(4001, "提交记录不存在", HttpStatus.NOT_FOUND),
    UNSUPPORTED_LANGUAGE(4002, "不支持的语言", HttpStatus.BAD_REQUEST),
    CODE_TOO_LONG(4003, "代码长度超出限制", HttpStatus.BAD_REQUEST),

    INTERNAL_ERROR(1500, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus status() {
        return status;
    }
}
