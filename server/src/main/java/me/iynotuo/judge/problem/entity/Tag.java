package me.iynotuo.judge.problem.entity;

import lombok.Data;

/**
 * 题目标签，如 "dp"、"图论"。
 */
@Data
public class Tag {

    private Long id;

    private String name;
}
