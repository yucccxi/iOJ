package me.iynotuo.judge.problem.dto;

import me.iynotuo.judge.common.enums.Difficulty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One entry of the unified catalog, which mixes local problems and external OJ links.
 *
 * <p>The response no longer carries a {@code kind} discriminator. Clients distinguish the two
 * shapes by their fields:</p>
 * <ul>
 *   <li>a local problem has a non-null {@code authorId}/{@code authorNickname}, a null
 *       {@code url}, and uses {@code scopeName} {@code LOCAL}; its {@code submitCount} and
 *       {@code acceptedCount} are counted by this service;</li>
 *   <li>an external OJ link has a non-null {@code url}, null author fields, and carries the
 *       imported counters reported by the external OJ. {@code code} is its original problem id and
 *       {@code time} is the clist contest time in China time (UTC+8), both possibly null.</li>
 * </ul>
 *
 * <p>Both shapes share {@code title}, {@code difficulty}, {@code rating},
 * {@code scopeId}/{@code scopeName} and {@code tags}, which is what makes a single filtered list
 * possible. An unrated item exposes {@code rating} as {@code 0} instead of {@code null}.</p>
 */
public record CatalogItemVO(Long id, String title, String code, Difficulty difficulty,
                            LocalDateTime time, Integer rating, Long scopeId, String scopeName, String url,
                            Integer submitCount, Integer acceptedCount, Long authorId, String authorNickname,
                            List<TagVO> tags, LocalDateTime createdAt) {

    public CatalogItemVO {
        rating = rating == null ? 0 : rating;
    }
}
