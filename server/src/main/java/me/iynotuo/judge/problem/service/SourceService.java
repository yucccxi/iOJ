package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.problem.dto.SourceMetaVO;
import me.iynotuo.judge.problem.dto.SourceSaveRequest;
import me.iynotuo.judge.problem.entity.ProblemSource;
import me.iynotuo.judge.problem.mapper.ProblemSourceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Problem source dictionary maintenance. Sources are administrator-managed and shared by local
 * problems and external OJ links; the public list (with usage counts) is served by
 * {@code GET /meta/sources}.
 */
@Service
public class SourceService {

    private final ProblemSourceMapper problemSourceMapper;

    public SourceService(ProblemSourceMapper problemSourceMapper) {
        this.problemSourceMapper = problemSourceMapper;
    }

    @Transactional
    public SourceMetaVO create(SourceSaveRequest request) {
        String name = request.name().trim();
        if (problemSourceMapper.findByName(name) != null) {
            throw BusinessException.of(ErrorCode.CONFLICT, "来源已存在");
        }
        ProblemSource source = new ProblemSource();
        source.setName(name);
        source.setCreatedAt(LocalDateTime.now());
        try {
            problemSourceMapper.insert(source);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(ErrorCode.CONFLICT, "来源已存在");
        }
        return new SourceMetaVO(source.getId(), source.getName(), source.getCreatedAt(), 0L);
    }

    @Transactional
    public SourceMetaVO rename(Long id, SourceSaveRequest request) {
        ProblemSource existing = requireSource(id);
        String name = request.name().trim();
        ProblemSource sameName = problemSourceMapper.findByName(name);
        if (sameName != null && !sameName.getId().equals(id)) {
            throw BusinessException.of(ErrorCode.CONFLICT, "来源已存在");
        }
        try {
            problemSourceMapper.updateName(id, name);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(ErrorCode.CONFLICT, "来源已存在");
        }
        return new SourceMetaVO(id, name, existing.getCreatedAt(), problemSourceMapper.countUsage(id));
    }

    /**
     * Deletes a source. Sources in use are rejected: local problems and external OJ links keep their
     * reference, so they must be moved to another source first.
     */
    @Transactional
    public void delete(Long id) {
        requireSource(id);
        long usage = problemSourceMapper.countUsage(id);
        if (usage > 0) {
            throw BusinessException.of(ErrorCode.CONFLICT, "来源仍被 " + usage + " 道题使用");
        }
        problemSourceMapper.deleteById(id);
    }

    private ProblemSource requireSource(Long id) {
        ProblemSource source = problemSourceMapper.findById(id);
        if (source == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "来源不存在");
        }
        return source;
    }
}
