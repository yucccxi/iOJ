package me.iynotuo.judge.common.enums;

/**
 * How multiple tag ids in a catalog search are combined.
 */
public enum TagMatchMode {
    /**
     * A problem matches when it has at least one of the requested tags.
     */
    ANY,

    /**
     * A problem matches only when it has every requested tag.
     */
    ALL
}
