package me.iynotuo.judge.problem.entity;

import lombok.Data;

/**
 * A problem tag, for example "dp" or "graphs".
 */
@Data
public class Tag {

    private Long id;

    private String name;
}
