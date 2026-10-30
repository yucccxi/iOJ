package me.iynotuo.judge.common.enums;

/**
 * Problem publication status.
 * DRAFT: visible to the author only; PENDING_REVIEW: waiting for review; PUBLISHED: live; HIDDEN: taken down or rejected.
 */
public enum ProblemStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    HIDDEN;

    public boolean visibleToPublic() {
        return this == PUBLISHED;
    }
}
