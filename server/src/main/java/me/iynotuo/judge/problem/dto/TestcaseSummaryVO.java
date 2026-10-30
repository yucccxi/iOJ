package me.iynotuo.judge.problem.dto;

import lombok.Data;

/**
 * 测试点元信息（不返回测试数据内容，避免列表响应过大）。
 */
@Data
public class TestcaseSummaryVO {

    private Long id;

    private Integer ordinal;

    private Integer inputBytes;

    private Integer outputBytes;

    private java.time.LocalDateTime createdAt;
}
