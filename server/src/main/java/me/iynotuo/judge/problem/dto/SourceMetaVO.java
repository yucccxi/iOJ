package me.iynotuo.judge.problem.dto;

import java.time.LocalDateTime;

/**
 * A problem source plus its usage, returned by the public reference-data endpoint.
 *
 * <p>{@code problemCount} counts local problems and external OJ links together, so the client can
 * render a source dropdown with usage hints without a second request.</p>
 */
public record SourceMetaVO(Long id, String name, LocalDateTime createdAt, long problemCount) {
}
