package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.ProblemStatus;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.config.ProblemProperties;
import me.iynotuo.judge.problem.dto.ProblemDetailVO;
import me.iynotuo.judge.problem.dto.ProblemSaveRequest;
import me.iynotuo.judge.problem.dto.ProblemSummaryVO;
import me.iynotuo.judge.problem.dto.SampleRequest;
import me.iynotuo.judge.problem.dto.SampleVO;
import me.iynotuo.judge.problem.dto.TagVO;
import me.iynotuo.judge.problem.entity.Problem;
import me.iynotuo.judge.problem.entity.ProblemSample;
import me.iynotuo.judge.problem.entity.Tag;
import me.iynotuo.judge.problem.mapper.ProblemCriteria;
import me.iynotuo.judge.problem.mapper.ProblemMapper;
import me.iynotuo.judge.problem.mapper.ProblemSampleMapper;
import me.iynotuo.judge.problem.mapper.ProblemSummaryRow;
import me.iynotuo.judge.problem.mapper.ProblemTagRow;
import me.iynotuo.judge.problem.mapper.ProblemTestcaseMapper;
import me.iynotuo.judge.problem.mapper.TagMapper;
import me.iynotuo.judge.security.LoginUser;
import me.iynotuo.judge.submission.mapper.SubmissionMapper;
import me.iynotuo.judge.user.entity.User;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Problem business logic: upload, edit, list and review.
 */
@Service
public class ProblemService {

    private final ProblemMapper problemMapper;
    private final ProblemSampleMapper sampleMapper;
    private final ProblemTestcaseMapper testcaseMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final SubmissionMapper submissionMapper;
    private final ProblemProperties properties;

    public ProblemService(ProblemMapper problemMapper,
                          ProblemSampleMapper sampleMapper,
                          ProblemTestcaseMapper testcaseMapper,
                          TagMapper tagMapper,
                          UserMapper userMapper,
                          SubmissionMapper submissionMapper,
                          ProblemProperties properties) {
        this.problemMapper = problemMapper;
        this.sampleMapper = sampleMapper;
        this.testcaseMapper = testcaseMapper;
        this.tagMapper = tagMapper;
        this.userMapper = userMapper;
        this.submissionMapper = submissionMapper;
        this.properties = properties;
    }

    @Transactional
    public ProblemDetailVO create(LoginUser current, ProblemSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Problem problem = new Problem();
        applyRequest(problem, request);
        problem.setAuthorId(current.id());
        // Publish immediately for administrators or when auto-publish is enabled; otherwise queue for review
        problem.setStatus(current.isAdmin() || properties.isAutoPublish()
                ? ProblemStatus.PUBLISHED : ProblemStatus.PENDING_REVIEW);
        problem.setSubmitCount(0);
        problem.setAcceptedCount(0);
        problem.setCreatedAt(now);
        problem.setUpdatedAt(now);
        problemMapper.insert(problem);
        replaceSamples(problem.getId(), request.samples());
        bindTags(problem.getId(), request.tags());
        return getDetail(problem.getId(), current);
    }

    @Transactional
    public ProblemDetailVO update(LoginUser current, Long id, ProblemSaveRequest request) {
        Problem problem = requireProblem(id);
        assertCanManage(current, problem);
        applyRequest(problem, request);
        problem.setUpdatedAt(LocalDateTime.now());
        problemMapper.update(problem);
        replaceSamples(id, request.samples());
        bindTags(id, request.tags());
        return getDetail(id, current);
    }

    @Transactional
    public void delete(LoginUser current, Long id) {
        Problem problem = requireProblem(id);
        assertCanManage(current, problem);
        problemMapper.deleteById(id);
    }

    /**
     * Public problem list: published problems only.
     */
    public PageResult<ProblemSummaryVO> page(String keyword, Difficulty difficulty, Long tagId,
                                             PageQuery query) {
        ProblemCriteria criteria = new ProblemCriteria();
        criteria.setKeyword(normalizeKeyword(keyword));
        criteria.setDifficulty(difficulty);
        criteria.setTagId(tagId);
        criteria.setStatuses(List.of(ProblemStatus.PUBLISHED));
        return queryPage(criteria, query);
    }

    /**
     * Problems uploaded by the current user, including drafts, pending review and hidden ones.
     */
    public PageResult<ProblemSummaryVO> pageMine(LoginUser current, String keyword, PageQuery query) {
        ProblemCriteria criteria = new ProblemCriteria();
        criteria.setKeyword(normalizeKeyword(keyword));
        criteria.setAuthorId(current.id());
        return queryPage(criteria, query);
    }

    /**
     * Administrator view: all problems, optionally filtered by status.
     */
    public PageResult<ProblemSummaryVO> pageForAdmin(String keyword, ProblemStatus status, PageQuery query) {
        ProblemCriteria criteria = new ProblemCriteria();
        criteria.setKeyword(normalizeKeyword(keyword));
        criteria.setStatuses(status == null ? null : List.of(status));
        return queryPage(criteria, query);
    }

    public ProblemDetailVO getDetail(Long id, LoginUser current) {
        Problem problem = requireProblem(id);
        if (!problem.getStatus().visibleToPublic() && cannotManage(current, problem)) {
            throw BusinessException.of(ErrorCode.PROBLEM_NOT_PUBLISHED);
        }
        List<SampleVO> samples = sampleMapper.findByProblemId(id).stream().map(SampleVO::from).toList();
        List<TagVO> tags = tagMapper.findByProblemId(id).stream()
                .map(tag -> new TagVO(tag.getId(), tag.getName()))
                .toList();
        String authorNickname = Optional.ofNullable(userMapper.findById(problem.getAuthorId()))
                .map(User::getNickname)
                .orElse("已注销用户");
        boolean solved = current != null && submissionMapper.countAccepted(current.id(), id) > 0;
        return new ProblemDetailVO(problem.getId(), problem.getTitle(), problem.getDifficulty(),
                problem.getDescription(), problem.getInputFormat(), problem.getOutputFormat(),
                problem.getHint(), problem.getSource(), problem.getTimeLimitMs(), problem.getMemoryLimitMb(),
                problem.getStatus(), problem.getAuthorId(), authorNickname, tags, samples,
                problem.getSubmitCount(), problem.getAcceptedCount(), solved,
                problem.getCreatedAt(), problem.getUpdatedAt(), problem.isHasChecker());
    }

    /**
     * Administrator review: publish or take down. A problem must have at least one test case before it can be published.
     */
    @Transactional
    public void updateStatus(Long id, ProblemStatus status) {
        if (status != ProblemStatus.PUBLISHED && status != ProblemStatus.HIDDEN) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "审核只支持 PUBLISHED 或 HIDDEN");
        }
        requireProblem(id);
        if (status == ProblemStatus.PUBLISHED && testcaseMapper.countByProblemId(id) == 0) {
            throw BusinessException.of(ErrorCode.PROBLEM_NO_TESTCASE);
        }
        problemMapper.updateStatus(id, status, LocalDateTime.now());
    }

    /**
     * Reused by the test case service: rejects callers who may not manage this problem.
     */
    public void assertManageable(LoginUser current, Long problemId) {
        assertCanManage(current, requireProblem(problemId));
    }

    /**
     * Replaces or clears the custom checker (special judge) of a problem.
     *
     * <p>Kept out of {@link #update} on purpose: that endpoint replaces the whole problem, so a
     * client that forgot to send the checker back would silently restore the built-in comparator and
     * change how every future submission is judged.</p>
     *
     * @param current the authenticated user; must be the author or an administrator
     * @param id      problem id
     * @param code    checker source, or {@code null}/blank to fall back to {@code OutputComparator}
     */
    @Transactional
    public void updateChecker(LoginUser current, Long id, String code) {
        Problem problem = requireProblem(id);
        assertCanManage(current, problem);
        problemMapper.updateChecker(id, StringUtils.hasText(code) ? code : null, LocalDateTime.now());
    }

    /**
     * Reads the custom checker source of a problem.
     *
     * @param current the authenticated user; must be the author or an administrator
     * @param id      problem id
     * @return the checker source, or an empty string when the problem uses the built-in comparator
     */
    public String getChecker(LoginUser current, Long id) {
        Problem problem = requireProblem(id);
        assertCanManage(current, problem);
        String code = problemMapper.findCheckerCode(id);
        return code == null ? "" : code;
    }

    private void applyRequest(Problem problem, ProblemSaveRequest request) {
        problem.setTitle(request.title().trim());
        problem.setDifficulty(request.difficulty());
        problem.setDescription(request.description());
        problem.setInputFormat(request.inputFormat());
        problem.setOutputFormat(request.outputFormat());
        problem.setHint(request.hint());
        problem.setSource(request.source());
        problem.setTimeLimitMs(request.timeLimitMs() == null ? 1000 : request.timeLimitMs());
        problem.setMemoryLimitMb(request.memoryLimitMb() == null ? 256 : request.memoryLimitMb());
    }

    private void replaceSamples(Long problemId, List<SampleRequest> samples) {
        sampleMapper.deleteByProblemId(problemId);
        if (samples == null || samples.isEmpty()) {
            return;
        }
        List<ProblemSample> entities = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            SampleRequest sample = samples.get(i);
            ProblemSample entity = new ProblemSample();
            entity.setProblemId(problemId);
            entity.setOrdinal(i + 1);
            entity.setInputData(sample.input());
            entity.setOutputData(sample.output());
            entity.setExplanation(sample.explanation());
            entities.add(entity);
        }
        sampleMapper.insertBatch(entities);
    }

    private void bindTags(Long problemId, List<String> tags) {
        tagMapper.deleteProblemTags(problemId);
        if (tags == null || tags.isEmpty()) {
            return;
        }
        LinkedHashSet<String> names = tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (names.isEmpty()) {
            return;
        }
        List<Long> tagIds = new ArrayList<>(names.size());
        for (String name : names) {
            tagIds.add(getOrCreateTag(name).getId());
        }
        tagMapper.insertProblemTags(problemId, tagIds);
    }

    private Tag getOrCreateTag(String name) {
        Tag existing = tagMapper.findByName(name);
        if (existing != null) {
            return existing;
        }
        Tag tag = new Tag();
        tag.setName(name);
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DuplicateKeyException ex) {
            // A concurrent request created the same tag; just read it back
            Tag created = tagMapper.findByName(name);
            if (created == null) {
                throw ex;
            }
            return created;
        }
    }

    private PageResult<ProblemSummaryVO> queryPage(ProblemCriteria criteria, PageQuery query) {
        long total = problemMapper.countPage(criteria);
        if (total == 0) {
            return PageResult.empty(query);
        }
        criteria.setOffset(query.offset());
        criteria.setLimit(query.size());
        List<ProblemSummaryRow> rows = problemMapper.findPage(criteria);
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), total, query);
        }
        List<Long> problemIds = rows.stream().map(ProblemSummaryRow::getId).toList();
        Map<Long, List<TagVO>> tagsByProblem = tagMapper.findByProblemIds(problemIds).stream()
                .collect(Collectors.groupingBy(ProblemTagRow::getProblemId,
                        Collectors.mapping(row -> new TagVO(row.getId(), row.getName()), Collectors.toList())));
        List<ProblemSummaryVO> items = rows.stream()
                .map(row -> new ProblemSummaryVO(row.getId(), row.getTitle(), row.getDifficulty(), row.getStatus(),
                        row.getSubmitCount(), row.getAcceptedCount(), row.getAuthorId(), row.getAuthorNickname(),
                        tagsByProblem.getOrDefault(row.getId(), List.of()), row.getCreatedAt()))
                .toList();
        return PageResult.of(items, total, query);
    }

    private boolean cannotManage(LoginUser current, Problem problem) {
        if (current == null) {
            return true;
        }
        return !current.isAdmin() && !current.isSelf(problem.getAuthorId());
    }

    private void assertCanManage(LoginUser current, Problem problem) {
        if (cannotManage(current, problem)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "只有题目作者或管理员可以操作该题目");
        }
    }

    private Problem requireProblem(Long id) {
        Problem problem = problemMapper.findById(id);
        if (problem == null) {
            throw BusinessException.of(ErrorCode.PROBLEM_NOT_FOUND);
        }
        return problem;
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }
}
