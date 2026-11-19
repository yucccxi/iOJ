package me.iynotuo.judge.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.SubmissionStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Submission detail. For callers other than the submitter, the problem author or an administrator, code, compileMessage and cases are blanked out.
 */
public record SubmissionDetailVO(Long id, Long problemId, String problemTitle, Long userId, String userNickname,
                                 Language language, @Schema(nullable = true) String code,
                                 SubmissionStatus status, String statusText,
                                 @Schema(nullable = true) Integer timeUsedMs,
                                 @Schema(nullable = true) Integer memoryUsedKb,
                                 @Schema(nullable = true) String compileMessage,
                                 @Schema(nullable = true) String judgeMessage,
                                 LocalDateTime createdAt, @Schema(nullable = true) LocalDateTime judgedAt,
                                 List<SubmissionCaseVO> cases) {
}
