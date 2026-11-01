package me.iynotuo.judge.problem.dto;

/**
 * A tag plus its usage, returned by the public reference-data endpoint.
 *
 * <p>{@code problemCount} counts local problems and external OJ links together, so the client can
 * show "数学 (12)" in filters or an overview without a second request. This is deliberately a
 * separate record from {@link TagVO}: problem responses keep the slim {@code {id, name}} shape.</p>
 */
public record TagMetaVO(Long id, String name, long problemCount) {
}
