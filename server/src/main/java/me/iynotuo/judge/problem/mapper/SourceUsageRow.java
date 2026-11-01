package me.iynotuo.judge.problem.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Source row with usage counts: {@code localCount} comes from {@code problems},
 * {@code externalCount} from {@code external_problems}.
 */
@Data
public class SourceUsageRow {

    private Long id;

    private String name;

    private LocalDateTime createdAt;

    private long localCount;

    private long externalCount;
}
