package me.iynotuo.judge.problem.dto;

/**
 * A tag plus its usage, returned by the public reference-data endpoint.
 *
 * <p>{@code problemCount} combines local problems and external OJ links. The public
 * {@code GET /meta/tags} counts only published local problems; administrator responses from
 * {@code /admin/tags} count local problems in every status. This is deliberately a separate record
 * from {@link TagVO}: problem responses keep the slim {@code {id, name}} shape.</p>
 */
public record TagMetaVO(Long id, String name, long problemCount) {
}
