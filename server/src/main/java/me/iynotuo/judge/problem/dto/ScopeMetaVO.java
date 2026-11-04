package me.iynotuo.judge.problem.dto;

import java.time.LocalDateTime;

/**
 * A problem scope plus its usage, returned by the public reference-data endpoint.
 *
 * <p>{@code problemCount} combines local problems and external OJ links. The public
 * {@code GET /meta/scopes} counts only published local problems; administrator responses from
 * {@code /admin/scopes} count local problems in every status.</p>
 */
public record ScopeMetaVO(Long id, String name, LocalDateTime createdAt, long problemCount) {
}
