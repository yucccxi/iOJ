package me.iynotuo.judge.submission.dto;

import me.iynotuo.judge.common.enums.Language;
import me.iynotuo.judge.common.enums.SubmissionStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交详情。非本人/题目作者/管理员查看时，code、compileMessage、cases 会被置空。
 */
public record SubmissionDetailVO(Long id, Long problemId, String problemTitle, Long userId, String userNickname,
                                 Language language, String code, SubmissionStatus status, String statusText,
                                 Integer timeUsedMs, Integer memoryUsedKb, String compileMessage, String judgeMessage,
                                 LocalDateTime createdAt, LocalDateTime judgedAt, List<SubmissionCaseVO> cases) {
}
