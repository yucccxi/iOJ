package me.iynotuo.judge.submission.service;

import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.SubmissionStatus;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.config.SubmissionProperties;
import me.iynotuo.judge.problem.entity.Problem;
import me.iynotuo.judge.problem.mapper.ProblemMapper;
import me.iynotuo.judge.problem.mapper.ProblemTestcaseMapper;
import me.iynotuo.judge.security.LoginUser;
import me.iynotuo.judge.submission.dto.SubmissionCaseVO;
import me.iynotuo.judge.submission.dto.SubmissionDetailVO;
import me.iynotuo.judge.submission.dto.SubmissionSummaryVO;
import me.iynotuo.judge.submission.dto.SubmitRequest;
import me.iynotuo.judge.submission.entity.Submission;
import me.iynotuo.judge.submission.entity.SubmissionCase;
import me.iynotuo.judge.submission.mapper.SubmissionCaseMapper;
import me.iynotuo.judge.submission.mapper.SubmissionCriteria;
import me.iynotuo.judge.submission.mapper.SubmissionMapper;
import me.iynotuo.judge.submission.mapper.SubmissionSummaryRow;
import me.iynotuo.judge.user.entity.User;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Code submission and submission history.
 */
@Service
public class SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionCaseMapper submissionCaseMapper;
    private final ProblemMapper problemMapper;
    private final ProblemTestcaseMapper testcaseMapper;
    private final UserMapper userMapper;
    private final JudgeProperties judgeProperties;
    private final SubmissionProperties submissionProperties;

    public SubmissionService(SubmissionMapper submissionMapper,
                             SubmissionCaseMapper submissionCaseMapper,
                             ProblemMapper problemMapper,
                             ProblemTestcaseMapper testcaseMapper,
                             UserMapper userMapper,
                             JudgeProperties judgeProperties,
                             SubmissionProperties submissionProperties) {
        this.submissionMapper = submissionMapper;
        this.submissionCaseMapper = submissionCaseMapper;
        this.problemMapper = problemMapper;
        this.testcaseMapper = testcaseMapper;
        this.userMapper = userMapper;
        this.judgeProperties = judgeProperties;
        this.submissionProperties = submissionProperties;
    }

    /**
     * Submits code. The row is stored as PENDING and picked up asynchronously by the judge worker.
     */
    @Transactional
    public SubmissionDetailVO submit(LoginUser current, SubmitRequest request) {
        Problem problem = problemMapper.findById(request.problemId());
        if (problem == null) {
            throw BusinessException.of(ErrorCode.PROBLEM_NOT_FOUND);
        }
        boolean manager = current.isAdmin() || current.isSelf(problem.getAuthorId());
        if (!problem.getStatus().visibleToPublic() && !manager) {
            throw BusinessException.of(ErrorCode.PROBLEM_NOT_PUBLISHED);
        }
        if (!judgeProperties.getLanguages().containsKey(request.language())) {
            throw BusinessException.of(ErrorCode.UNSUPPORTED_LANGUAGE);
        }
        if (request.code().length() > submissionProperties.getMaxCodeLength()) {
            throw BusinessException.of(ErrorCode.CODE_TOO_LONG);
        }
        if (testcaseMapper.countByProblemId(problem.getId()) == 0) {
            throw BusinessException.of(ErrorCode.PROBLEM_NO_TESTCASE);
        }

        Submission submission = new Submission();
        submission.setProblemId(problem.getId());
        submission.setUserId(current.id());
        submission.setLanguage(request.language());
        submission.setCode(request.code());
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setCreatedAt(LocalDateTime.now());
        submissionMapper.insert(submission);
        problemMapper.increaseSubmitCount(problem.getId());
        return getDetail(submission.getId(), current);
    }

    /**
     * Submission detail. Source code, compiler output and per-case results are only visible to the submitter, the problem author and administrators.
     */
    public SubmissionDetailVO getDetail(Long id, LoginUser current) {
        Submission submission = requireSubmission(id);
        Problem problem = problemMapper.findById(submission.getProblemId());
        boolean privileged = canSeePrivate(current, submission, problem);
        List<SubmissionCaseVO> cases = privileged
                ? submissionCaseMapper.findBySubmissionId(id).stream()
                        .map(this::toCaseVO)
                        .toList()
                : List.of();
        String problemTitle = problem == null ? "已删除的题目" : problem.getTitle();
        String userNickname = Optional.ofNullable(userMapper.findById(submission.getUserId()))
                .map(User::getNickname)
                .orElse("已注销用户");
        return new SubmissionDetailVO(submission.getId(), submission.getProblemId(), problemTitle,
                submission.getUserId(), userNickname, submission.getLanguage(),
                privileged ? submission.getCode() : null,
                submission.getStatus(), submission.getStatus().description(),
                submission.getTimeUsedMs(), submission.getMemoryUsedKb(),
                privileged ? submission.getCompileMessage() : null,
                privileged ? submission.getJudgeMessage() : null,
                submission.getCreatedAt(), submission.getJudgedAt(), cases);
    }

    /**
     * Public submission list; source code is never included.
     */
    public PageResult<SubmissionSummaryVO> page(Long problemId, Long userId, SubmissionStatus status,
                                                PageQuery query) {
        SubmissionCriteria criteria = new SubmissionCriteria();
        criteria.setProblemId(problemId);
        criteria.setUserId(userId);
        criteria.setStatus(status);

        long total = submissionMapper.countPage(criteria);
        if (total == 0) {
            return PageResult.empty(query);
        }
        criteria.setOffset(query.offset());
        criteria.setLimit(query.size());
        List<SubmissionSummaryRow> rows = submissionMapper.findPage(criteria);
        List<SubmissionSummaryVO> items = rows.stream()
                .map(row -> new SubmissionSummaryVO(row.getId(), row.getProblemId(), row.getProblemTitle(),
                        row.getUserId(), row.getUserNickname(), row.getLanguage(), row.getStatus(),
                        row.getStatus().description(), row.getTimeUsedMs(), row.getMemoryUsedKb(),
                        row.getCreatedAt()))
                .toList();
        return PageResult.of(items, total, query);
    }

    private SubmissionCaseVO toCaseVO(SubmissionCase item) {
        return new SubmissionCaseVO(item.getCaseOrdinal(), item.getStatus(), item.getStatus().description(),
                item.getTimeUsedMs(), item.getMemoryUsedKb(), item.getMessage());
    }

    private boolean canSeePrivate(LoginUser current, Submission submission, Problem problem) {
        if (current == null) {
            return false;
        }
        if (current.isAdmin() || current.isSelf(submission.getUserId())) {
            return true;
        }
        return problem != null && current.isSelf(problem.getAuthorId());
    }

    private Submission requireSubmission(Long id) {
        Submission submission = submissionMapper.findById(id);
        if (submission == null) {
            throw BusinessException.of(ErrorCode.SUBMISSION_NOT_FOUND);
        }
        return submission;
    }
}
