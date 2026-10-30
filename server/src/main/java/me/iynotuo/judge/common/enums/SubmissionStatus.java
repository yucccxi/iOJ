package me.iynotuo.judge.common.enums;

/**
 * Submission status, also used as the result of a single test case.
 */
public enum SubmissionStatus {

    PENDING("排队中"),
    JUDGING("评测中"),
    ACCEPTED("通过"),
    WRONG_ANSWER("答案错误"),
    TIME_LIMIT_EXCEEDED("超出时间限制"),
    MEMORY_LIMIT_EXCEEDED("超出内存限制"),
    OUTPUT_LIMIT_EXCEEDED("输出超出限制"),
    RUNTIME_ERROR("运行错误"),
    COMPILATION_ERROR("编译错误"),
    SYSTEM_ERROR("系统错误");

    private final String description;

    SubmissionStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public boolean isFinished() {
        return this != PENDING && this != JUDGING;
    }

    public boolean isAccepted() {
        return this == ACCEPTED;
    }
}
