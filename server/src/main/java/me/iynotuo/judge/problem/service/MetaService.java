package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.core.config.JudgeProperties;
import me.iynotuo.judge.problem.dto.LanguageVO;
import me.iynotuo.judge.problem.dto.ScopeMetaVO;
import me.iynotuo.judge.problem.dto.TagMetaVO;
import me.iynotuo.judge.problem.mapper.ProblemScopeMapper;
import me.iynotuo.judge.problem.mapper.TagMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Public reference data for client initialisation: available languages, tags and problem scopes.
 *
 * <p>Tags and scopes include their usage count (local problems + external OJ links); the lists are
 * public, while creating/renaming/deleting them is restricted to administrators.</p>
 */
@Service
public class MetaService {

    private final JudgeProperties judgeProperties;
    private final TagMapper tagMapper;
    private final ProblemScopeMapper problemScopeMapper;

    public MetaService(JudgeProperties judgeProperties, TagMapper tagMapper,
                       ProblemScopeMapper problemScopeMapper) {
        this.judgeProperties = judgeProperties;
        this.tagMapper = tagMapper;
        this.problemScopeMapper = problemScopeMapper;
    }

    public List<LanguageVO> languages() {
        return judgeProperties.getLanguages().entrySet().stream()
                .map(entry -> new LanguageVO(entry.getKey().name(),
                        entry.getValue().getDisplayName() == null
                                ? entry.getKey().name() : entry.getValue().getDisplayName()))
                .toList();
    }

    /**
     * Tags selectable when labelling a problem, with their usage count (local + external).
     */
    public List<TagMetaVO> tags() {
        return tagMapper.findAllWithUsage().stream()
                .map(row -> new TagMetaVO(row.getId(), row.getName(),
                        row.getLocalCount() + row.getExternalCount()))
                .toList();
    }

    /**
     * Scopes selectable in the search filters, with their usage count.
     */
    public List<ScopeMetaVO> scopes() {
        return problemScopeMapper.findAllWithUsage().stream()
                .map(row -> new ScopeMetaVO(row.getId(), row.getName(), row.getCreatedAt(),
                        row.getLocalCount() + row.getExternalCount()))
                .toList();
    }
}
