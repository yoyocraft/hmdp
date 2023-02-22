package com.hmdp.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动刷新返回结果类
 *
 * @author codejuzi
 */
@Data
public class ScrollResult {
    /**
     * 小于指定时间戳的数据集合
     */
    private List<?> list;

    /**
     * 本次查询的最小时间吹
     */
    private Long minTime;

    /**
     * 偏移量
     */
    private Integer offset;
}
