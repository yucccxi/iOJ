package me.iynotuo.judge.submission.dto;

import me.iynotuo.judge.common.enums.Language;
import me.iynotuo.judge.common.enums.SubmissionStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Submission detail. For callers other than the submitter, the problem author or an administrator, code, compileMessage and cases are blanked out.
 */
public record SubmissionDetailVO(Long id, Long problemId, String problemTitle, Long userId, String userNickname,
                                 Language language, String code, SubmissionStatus status, String statusText,
                                 Integer timeUsedMs, Integer memoryUsedKb, String compileMessage, String judgeMessage,
                                 LocalDateTime createdAt, LocalDateTime judgedAt, List<SubmissionCaseVO> cases) {
}
