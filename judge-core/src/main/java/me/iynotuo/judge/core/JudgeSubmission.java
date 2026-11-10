package me.iynotuo.judge.core;

import me.iynotuo.judge.protocol.Language;

/**
 * The submission data a judge engine needs, decoupled from the database entity.
 *
 * @param id       submission id, used in logs and case results
 * @param code     source code supplied by the contestant
 * @param language language selected for this submission
 */
public record JudgeSubmission(Long id, String code, Language language) {
}
