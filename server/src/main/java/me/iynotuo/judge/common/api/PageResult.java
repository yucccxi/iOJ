package me.iynotuo.judge.common.api;

import java.util.List;

/**
 * A page of results.
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResult<T> of(List<T> items, long total, PageQuery query) {
        return new PageResult<>(items, total, query.page(), query.size());
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), 0L, query.page(), query.size());
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) ((total + size - 1) / size);
    }
}
