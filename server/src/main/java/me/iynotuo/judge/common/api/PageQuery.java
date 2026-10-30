package me.iynotuo.judge.common.api;

/**
 * Pagination parameters. page is 1-based and size is capped at 100; out-of-range values are clamped so huge pages cannot overload the database.
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
