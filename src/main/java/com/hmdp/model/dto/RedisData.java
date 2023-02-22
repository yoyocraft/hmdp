package com.hmdp.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    /**
     * 逻辑过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 存储的数据
     */
    private Object data;
}
