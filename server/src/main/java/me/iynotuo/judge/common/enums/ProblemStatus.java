package me.iynotuo.judge.common.enums;

/**
 * 题目状态。
 * DRAFT: 草稿（仅作者可见）；PENDING_REVIEW: 待审核；PUBLISHED: 已发布；HIDDEN: 已下架/驳回。
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
