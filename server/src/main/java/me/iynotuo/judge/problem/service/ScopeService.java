package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.problem.dto.ScopeMetaVO;
import me.iynotuo.judge.problem.dto.ScopeSaveRequest;
import me.iynotuo.judge.problem.entity.ProblemScope;
import me.iynotuo.judge.problem.mapper.ProblemScopeMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Problem scope dictionary maintenance. Scopes are administrator-managed: {@code LOCAL} is reserved
 * for locally authored problems, and the other rows are used by external OJ links. The public list
 * (with usage counts) is served by {@code GET /meta/scopes}.
 */
@Service
public class ScopeService {

    private final ProblemScopeMapper problemScopeMapper;

    public ScopeService(ProblemScopeMapper problemScopeMapper) {
        this.problemScopeMapper = problemScopeMapper;
    }

    @Transactional
    public ScopeMetaVO create(ScopeSaveRequest request) {
        String name = request.name().trim();
        if (problemScopeMapper.findByName(name) != null) {
            throw BusinessException.of(ErrorCode.CONFLICT, "scope 已存在");
        }
        ProblemScope scope = new ProblemScope();
        scope.setName(name);
        scope.setCreatedAt(LocalDateTime.now());
        try {
            problemScopeMapper.insert(scope);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(ErrorCode.CONFLICT, "scope 已存在");
        }
        return new ScopeMetaVO(scope.getId(), scope.getName(), scope.getCreatedAt(), 0L);
    }

    @Transactional
    public ScopeMetaVO rename(Long id, ScopeSaveRequest request) {
        ProblemScope existing = requireScope(id);
        assertNotReserved(existing);
        String name = request.name().trim();
        ProblemScope sameName = problemScopeMapper.findByName(name);
        if (sameName != null && !sameName.getId().equals(id)) {
            throw BusinessException.of(ErrorCode.CONFLICT, "scope 已存在");
        }
        try {
            problemScopeMapper.updateName(id, name);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(ErrorCode.CONFLICT, "scope 已存在");
        }
        return new ScopeMetaVO(id, name, existing.getCreatedAt(), problemScopeMapper.countUsage(id));
    }

    /**
     * Deletes a scope. Scopes in use are rejected because problem or external-link rows still
     * reference them, so those rows must be reassigned or removed first. The reserved {@code LOCAL}
     * scope cannot be deleted at all.
     */
    @Transactional
    public void delete(Long id) {
        assertNotReserved(requireScope(id));
        long usage = problemScopeMapper.countUsage(id);
        if (usage > 0) {
            throw BusinessException.of(ErrorCode.CONFLICT, "scope 仍被 " + usage + " 道题使用");
        }
        problemScopeMapper.deleteById(id);
    }

    private ProblemScope requireScope(Long id) {
        ProblemScope scope = problemScopeMapper.findById(id);
        if (scope == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "scope 不存在");
        }
        return scope;
    }

    /**
     * LOCAL is referenced by every local problem and is resolved by name on upload, so renaming or
     * deleting it would break future problem creation.
     */
    private void assertNotReserved(ProblemScope scope) {
        if (ProblemScope.LOCAL.equalsIgnoreCase(scope.getName())) {
            throw BusinessException.of(ErrorCode.CONFLICT, "LOCAL 是系统保留 scope，不能重命名或删除");
        }
    }
}
