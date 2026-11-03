package me.iynotuo.judge.problem.mapper;

import lombok.Data;
import me.iynotuo.judge.common.enums.Difficulty;

import java.time.LocalDateTime;

/**
 * Raw row of the unified catalog query. {@code kind} is the constant column produced by the UNION;
 * the LOCAL branch fills the counters and author fields, the EXTERNAL branch leaves them null and
 * fills {@code code} and {@code time} instead.
 */
@Data
public class CatalogRow {

    private String kind;

    private Long refId;

    private String title;

    /**
     * Short identifier of the original OJ problem; null for local problems.
     */
    private String code;

    private Difficulty difficulty;

    /**
     * Contest time of an external problem in China time (UTC+8); null for local problems.
     */
    private LocalDateTime time;

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
