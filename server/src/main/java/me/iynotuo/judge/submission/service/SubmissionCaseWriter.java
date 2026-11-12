package me.iynotuo.judge.submission.service;

import me.iynotuo.judge.submission.entity.SubmissionCase;
import me.iynotuo.judge.submission.mapper.SubmissionCaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces the per-case results of one submission.
 *
 * <p>The previous implementation deleted every case and inserted the new rows. On MySQL/InnoDB the
 * {@code DELETE} of a not-yet-existing {@code (submission_id, case_ordinal)} range still takes a gap
 * lock; two concurrent results for adjacent submissions then deadlocked on their insert intention
 * locks. This writer upserts first and deletes only ordinals that disappeared, so a first-time
 * result issues no {@code DELETE} at all and the hot path stays free of that gap lock.</p>
 */
@Service
public class SubmissionCaseWriter {

    private final SubmissionCaseMapper submissionCaseMapper;

    public SubmissionCaseWriter(SubmissionCaseMapper submissionCaseMapper) {
        this.submissionCaseMapper = submissionCaseMapper;
    }

    /**
     * Stores {@code cases} as the complete result of one submission.
     *
     * <p>Rows are upserted by {@code (submission_id, case_ordinal)}. Ordinals that exist in the
     * database but are absent from {@code cases} are deleted, which handles a rejudge whose result
     * has fewer test cases. The read-then-write sequence is safe because callers serialize writers
     * for one submission: the remote path locks the submission row, and the local worker claims each
     * submission once before judging it.</p>
     *
     * @param submissionId submission whose case rows are replaced
     * @param cases        new rows; their submission id must already be set. May be empty, in which
     *                     case existing rows are removed
     */
    @Transactional
    public void replace(Long submissionId, List<SubmissionCase> cases) {
        List<SubmissionCase> previous = submissionCaseMapper.findBySubmissionId(submissionId);
        if (cases == null || cases.isEmpty()) {
            if (!previous.isEmpty()) {
                submissionCaseMapper.deleteBySubmissionId(submissionId);
            }
            return;
        }

        submissionCaseMapper.upsertBatch(cases);

        Set<Integer> currentOrdinals = new HashSet<>(cases.size());
        for (SubmissionCase item : cases) {
            currentOrdinals.add(item.getCaseOrdinal());
        }
        List<Integer> staleOrdinals = previous.stream()
                .map(SubmissionCase::getCaseOrdinal)
                .filter(ordinal -> !currentOrdinals.contains(ordinal))
                .toList();
        if (!staleOrdinals.isEmpty()) {
            submissionCaseMapper.deleteBySubmissionIdAndOrdinals(submissionId, staleOrdinals);
        }
    }
}
