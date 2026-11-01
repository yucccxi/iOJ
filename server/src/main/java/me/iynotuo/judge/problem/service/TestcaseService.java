package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.config.ProblemProperties;
import me.iynotuo.judge.problem.dto.TestcaseDetailVO;
import me.iynotuo.judge.problem.dto.TestcaseRequest;
import me.iynotuo.judge.problem.dto.TestcaseSummaryVO;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import me.iynotuo.judge.problem.mapper.ProblemTestcaseMapper;
import me.iynotuo.judge.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD for hidden test cases. Every method checks that the caller is the author or an administrator.
 */
@Service
public class TestcaseService {

    private final ProblemService problemService;
    private final ProblemTestcaseMapper testcaseMapper;
    private final ProblemProperties properties;

    public TestcaseService(ProblemService problemService,
                           ProblemTestcaseMapper testcaseMapper,
                           ProblemProperties properties) {
        this.problemService = problemService;
        this.testcaseMapper = testcaseMapper;
        this.properties = properties;
    }

    public List<TestcaseSummaryVO> list(LoginUser current, Long problemId) {
        problemService.assertManageable(current, problemId);
        return testcaseMapper.findSummaries(problemId);
    }

    public TestcaseDetailVO get(LoginUser current, Long problemId, Long testcaseId) {
        problemService.assertManageable(current, problemId);
        ProblemTestcase testcase = testcaseMapper.findById(testcaseId);
        if (testcase == null || !testcase.getProblemId().equals(problemId)) {
            throw BusinessException.of(ErrorCode.TESTCASE_NOT_FOUND);
        }
        return TestcaseDetailVO.from(testcase);
    }

    @Transactional
    public TestcaseSummaryVO add(LoginUser current, Long problemId, TestcaseRequest request) {
        problemService.assertManageable(current, problemId);
        if (testcaseMapper.countByProblemId(problemId) >= properties.getMaxTestcases()) {
            throw BusinessException.of(ErrorCode.TESTCASE_LIMIT_EXCEEDED);
        }
        validate(request);
        ProblemTestcase testcase = new ProblemTestcase();
        testcase.setProblemId(problemId);
        testcase.setOrdinal(testcaseMapper.findMaxOrdinal(problemId) + 1);
        testcase.setInputData(request.input());
        testcase.setOutputData(request.output());
        testcase.setCreatedAt(LocalDateTime.now());
        testcaseMapper.insertBatch(List.of(testcase));
        // insertBatch does not return generated keys, so read the row back by the (problem_id, ordinal) unique key
        TestcaseSummaryVO summary = testcaseMapper.findSummary(problemId, testcase.getOrdinal());
        if (summary == null) {
            throw BusinessException.of(ErrorCode.TESTCASE_NOT_FOUND);
        }
        return summary;
    }

    /**
     * Replaces all test cases at once, which suits the upload-then-save-once client flow.
     */
    @Transactional
    public List<TestcaseSummaryVO> replace(LoginUser current, Long problemId, List<TestcaseRequest> requests) {
        problemService.assertManageable(current, problemId);
        if (requests.size() > properties.getMaxTestcases()) {
            throw BusinessException.of(ErrorCode.TESTCASE_LIMIT_EXCEEDED);
        }
        requests.forEach(this::validate);
        testcaseMapper.deleteByProblemId(problemId);
        if (requests.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<ProblemTestcase> entities = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            ProblemTestcase entity = new ProblemTestcase();
            entity.setProblemId(problemId);
            entity.setOrdinal(i + 1);
            entity.setInputData(requests.get(i).input());
            entity.setOutputData(requests.get(i).output());
            entity.setCreatedAt(now);
            entities.add(entity);
        }
        testcaseMapper.insertBatch(entities);
        return testcaseMapper.findSummaries(problemId);
    }

    @Transactional
    public void delete(LoginUser current, Long problemId, Long testcaseId) {
        problemService.assertManageable(current, problemId);
        int deleted = testcaseMapper.deleteById(problemId, testcaseId);
        if (deleted == 0) {
            throw BusinessException.of(ErrorCode.TESTCASE_NOT_FOUND);
        }
    }

    private void validate(TestcaseRequest request) {
        int maxBytes = properties.getMaxTestcaseBytes();
        if (request.input().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST,
                    "测试点输入不能超过 " + maxBytes + " 字节");
        }
        if (request.output().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST,
                    "测试点输出不能超过 " + maxBytes + " 字节");
        }
    }
}
