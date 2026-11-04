package me.iynotuo.judge.problem.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Scope row with usage counts: {@code localCount} comes from {@code problems},
 * {@code externalCount} from {@code external_problems}.
 */
@Data
public class ScopeUsageRow {

    private Long id;

    private String name;

    private LocalDateTime createdAt;

    private long localCount;

    private long externalCount;
}
