package com.aiplatform.foundation.shared.contract.paging;

/**
 * 页码分页查询。
 *
 * @param pageNumber 从 1 开始的页码
 * @param pageSize 每页数量
 */
public record PageQuery(long pageNumber, long pageSize) {

    public static final long DEFAULT_PAGE_NUMBER = 1L;
    public static final long DEFAULT_PAGE_SIZE = 20L;
    public static final long MAX_PAGE_SIZE = 100L;
    public static final long DEFAULT_MAX_OFFSET = 100_000L;

    public PageQuery {
        if (pageNumber < 1L) {
            throw new IllegalArgumentException("页码必须从 1 开始");
        }
        if (pageSize < 1L || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页数量必须在 1 到 100 之间");
        }
        validateOffset(pageNumber, pageSize, DEFAULT_MAX_OFFSET);
    }

    public static PageQuery defaults() {
        return new PageQuery(DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE);
    }

    public long offset() {
        return Math.multiplyExact(pageNumber - 1L, pageSize);
    }

    public void requireMaxOffset(long maxOffset) {
        validateOffset(pageNumber, pageSize, maxOffset);
    }

    private static void validateOffset(
            long pageNumber,
            long pageSize,
            long maxOffset) {
        if (maxOffset < 0L || maxOffset > DEFAULT_MAX_OFFSET) {
            throw new IllegalArgumentException("最大偏移量必须在平台上限内");
        }
        final long offset;
        try {
            offset = Math.multiplyExact(pageNumber - 1L, pageSize);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("分页偏移量超出范围", exception);
        }
        if (offset > maxOffset) {
            throw new IllegalArgumentException("分页偏移量超过允许上限");
        }
    }
}
