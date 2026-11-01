package me.iynotuo.judge.problem.dto;

import me.iynotuo.judge.common.enums.Difficulty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One entry of the unified catalog, which mixes local problems and external OJ links.
 *
 * <p>{@code kind} decides how the client renders the row and which detail endpoint it calls:</p>
 * <ul>
 *   <li>{@code LOCAL} — a problem hosted here: {@code GET /problems/{id}}, {@code difficulty},
 *       {@code submitCount}, {@code acceptedCount}, {@code authorId} and {@code authorNickname} are
 *       filled in, {@code url} is null;</li>
 *   <li>{@code EXTERNAL} — a link to another OJ: {@code url} is set, while {@code difficulty},
 *       {@code submitCount}, {@code acceptedCount}, {@code authorId} and {@code authorNickname} are
 *       null because that data does not exist locally.</li>
 * </ul>
 *
 * <p>Both kinds share {@code title}, {@code rating}, {@code sourceId}/{@code sourceName} and
 * {@code tags}, which is what makes a single filtered list possible.</p>
 */
public record CatalogItemVO(String kind, Long id, String title, Difficulty difficulty, Integer rating,
                            Long sourceId, String sourceName, String url,
                            Integer submitCount, Integer acceptedCount, Long authorId, String authorNickname,
                            List<TagVO> tags, LocalDateTime createdAt) {
}

