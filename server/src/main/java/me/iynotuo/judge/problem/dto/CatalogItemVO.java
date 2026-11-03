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
 *       filled in, while {@code code}, {@code time} and {@code url} are null;</li>
 *   <li>{@code EXTERNAL} — a link to another OJ: {@code url}, {@code code} and {@code difficulty}
 *       are set and {@code submitCount}/{@code acceptedCount} are whatever the source OJ reports
 *       (imported, not counted here), while {@code authorId} and {@code authorNickname} are null
 *       because that data does not exist locally. {@code difficulty} is derived from the source
 *       rating: Codeforces &lt;1400 / 1400-1999 / &gt;=2000, AtCoder &lt;800 / 800-1599 / &gt;=1600;
 *       a missing rating produces {@code UNKNOWN}. {@code time} is the clist contest time in China
 *       time (UTC+8), or null when clist did not provide one.</li>
 * </ul>
 *
 * <p>Both kinds share {@code title}, {@code difficulty}, {@code rating},
 * {@code sourceId}/{@code sourceName} and {@code tags}, which is what makes a single filtered list
 * possible. An unrated item exposes {@code rating} as {@code 0} instead of {@code null}.</p>
 */
public record CatalogItemVO(String kind, Long id, String title, String code, Difficulty difficulty,
                            LocalDateTime time, Integer rating, Long sourceId, String sourceName, String url,
                            Integer submitCount, Integer acceptedCount, Long authorId, String authorNickname,
                            List<TagVO> tags, LocalDateTime createdAt) {

    public CatalogItemVO {
        rating = rating == null ? 0 : rating;
    }
}

