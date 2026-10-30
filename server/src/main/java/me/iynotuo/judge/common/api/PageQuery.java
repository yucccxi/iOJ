package me.iynotuo.judge.common.api;

/**
 * 分页参数。page 从 1 开始，size 最大 100，越界时自动收敛到合法区间，避免恶意大分页拖垮数据库。
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
