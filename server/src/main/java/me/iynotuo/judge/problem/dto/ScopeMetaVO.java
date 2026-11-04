package me.iynotuo.judge.problem.dto;

import java.time.LocalDateTime;

/**
 * A problem scope plus its usage, returned by the public reference-data endpoint.
 *
 * <p>{@code problemCount} counts local problems and external OJ links together, so the client can
 * render a scope dropdown with usage hints without a second request.</p>
 */
public record ScopeMetaVO(Long id, String name, LocalDateTime createdAt, long problemCount) {
}
