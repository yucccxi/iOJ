package me.iynotuo.judge.submission.entity;

import lombok.Data;
import me.iynotuo.judge.common.enums.Language;
import me.iynotuo.judge.common.enums.SubmissionStatus;

import java.time.LocalDateTime;

/**
 * 提交记录。code 可能较大，仅在详情接口对本人/管理员/题目作者可见。
 */
@Data
public class Submission {

    private Long id;

    private Long problemId;

    private Long userId;

    private Language language;

    private String code;

    private SubmissionStatus status;

    private Integer timeUsedMs;

    private Integer memoryUsedKb;

    /**
     * 编译输出，编译错误时用于展示。
     */
    private String compileMessage;

    /**
     * 系统级错误信息（如测试点缺失），不包含测试数据。
     */
    private String judgeMessage;

    private LocalDateTime createdAt;

    private LocalDateTime judgedAt;
}
