package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.problem.dto.LanguageVO;
import me.iynotuo.judge.problem.dto.TagVO;
import me.iynotuo.judge.problem.mapper.TagMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reference data for client initialisation: available languages and tags.
 */
@Service
public class MetaService {

    private final JudgeProperties judgeProperties;
    private final TagMapper tagMapper;

    public MetaService(JudgeProperties judgeProperties, TagMapper tagMapper) {
        this.judgeProperties = judgeProperties;
        this.tagMapper = tagMapper;
    }

    public List<LanguageVO> languages() {
        return judgeProperties.getLanguages().entrySet().stream()
                .map(entry -> new LanguageVO(entry.getKey().name(),
                        entry.getValue().getDisplayName() == null
                                ? entry.getKey().name() : entry.getValue().getDisplayName()))
                .toList();
    }

    public List<TagVO> tags() {
        return tagMapper.findAll().stream()
                .map(tag -> new TagVO(tag.getId(), tag.getName()))
                .toList();
    }
}
