package me.iynotuo.judge.protocol;

import java.util.List;

/**
 * Tasks returned by a claim request.
 *
 * <p>An empty list is equivalent to "no work right now"; a server may also answer with HTTP 204.
 * Returning a list keeps batch claiming possible without a second endpoint.</p>
 *
 * @param tasks claimed tasks, possibly empty
 */
public record JudgeTaskClaimResponse(List<JudgeTask> tasks) {

    public JudgeTaskClaimResponse {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
