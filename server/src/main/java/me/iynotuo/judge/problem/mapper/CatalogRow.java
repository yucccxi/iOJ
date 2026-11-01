package me.iynotuo.judge.problem.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;

import java.time.LocalDateTime;

/**
 * Raw row of the unified catalog query. {@code kind} is the constant column produced by the UNION;
 * the LOCAL branch fills the counters and author fields, the EXTERNAL branch leaves them null.
 */
@Data
public class CatalogRow {

    private String kind;

    private Long refId;

    private String title;

    private Difficulty difficulty;

    private Integer rating;

    private Long sourceId;

    private String sourceName;

    private String url;

    private Integer submitCount;

    private Integer acceptedCount;

    private Long authorId;

    private String authorNickname;

    private LocalDateTime createdAt;
}
