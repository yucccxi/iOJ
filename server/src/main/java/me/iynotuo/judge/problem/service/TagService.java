package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.problem.dto.TagMetaVO;
import me.iynotuo.judge.problem.dto.TagSaveRequest;
import me.iynotuo.judge.problem.entity.Tag;
import me.iynotuo.judge.problem.mapper.ExternalProblemTagMapper;
import me.iynotuo.judge.problem.mapper.TagMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tag dictionary maintenance. Tags are administrator-managed: the public list (with usage counts) is
 * served by {@code GET /meta/tags}, and problem authors can only pick existing ids.
 */
@Service
public class TagService {

    private final TagMapper tagMapper;
    private final ExternalProblemTagMapper externalProblemTagMapper;

    public TagService(TagMapper tagMapper, ExternalProblemTagMapper externalProblemTagMapper) {
        this.tagMapper = tagMapper;
        this.externalProblemTagMapper = externalProblemTagMapper;
    }

    @Transactional
    public TagMetaVO create(TagSaveRequest request) {
        String name = request.name().trim();
        if (tagMapper.findByName(name) != null) {
            throw BusinessException.of(ErrorCode.CONFLICT, "标签已存在");
        }
        Tag tag = new Tag();
        tag.setName(name);
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException ex) {
            // Concurrent creation of the same name: report the same conflict instead of a 500
            throw BusinessException.of(ErrorCode.CONFLICT, "标签已存在");
        }
        return new TagMetaVO(tag.getId(), tag.getName(), 0L);
    }

    @Transactional
    public TagMetaVO rename(Long id, TagSaveRequest request) {
        requireTag(id);
        String name = request.name().trim();
        Tag sameName = tagMapper.findByName(name);
        if (sameName != null && !sameName.getId().equals(id)) {
            throw BusinessException.of(ErrorCode.CONFLICT, "标签已存在");
        }
        try {
            tagMapper.updateName(id, name);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(ErrorCode.CONFLICT, "标签已存在");
        }
        return new TagMetaVO(id, name, tagMapper.countUsage(id));
    }

    /**
     * Deletes a tag.
     *
     * @param force when true, the tag is also removed from every local problem and external OJ link;
     *              when false a tag that is still in use is rejected with a conflict
     */
    @Transactional
    public void delete(Long id, boolean force) {
        requireTag(id);
        long usage = tagMapper.countUsage(id);
        if (usage > 0) {
            if (!force) {
                throw BusinessException.of(ErrorCode.CONFLICT, "标签仍被 " + usage + " 道题使用");
            }
            tagMapper.deleteProblemTagsByTagId(id);
            externalProblemTagMapper.deleteByTagId(id);
        }
        tagMapper.deleteById(id);
    }

    private void requireTag(Long id) {
        if (tagMapper.findById(id) == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "标签不存在");
        }
    }
}
