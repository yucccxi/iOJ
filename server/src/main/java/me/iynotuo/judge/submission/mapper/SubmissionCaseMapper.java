package me.iynotuo.judge.submission.mapper;

import me.iynotuo.judge.submission.entity.SubmissionCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubmissionCaseMapper {

    /**
     * Inserts the case rows, updating the mutable columns when the
     * {@code (submission_id, case_ordinal)} key already exists.
     *
     * <p>Used instead of a delete-all plus insert so the first result of a submission does not take
     * the gap lock that a {@code DELETE} of a not-yet-existing range would acquire.</p>
     *
     * @param cases rows to insert or update; must not be empty
     * @return the number of affected rows
     */
    int upsertBatch(@Param("cases") List<SubmissionCase> cases);

    List<SubmissionCase> findBySubmissionId(@Param("submissionId") Long submissionId);

    int deleteBySubmissionId(@Param("submissionId") Long submissionId);

    /**
     * Deletes only the listed ordinals of one submission, used to clean up cases that disappeared
     * after a rejudge.
     *
     * @param submissionId submission whose cases are pruned
     * @param ordinals     ordinals to delete; must not be empty
     * @return the number of deleted rows
     */
    int deleteBySubmissionIdAndOrdinals(@Param("submissionId") Long submissionId,
                                        @Param("ordinals") List<Integer> ordinals);
}
