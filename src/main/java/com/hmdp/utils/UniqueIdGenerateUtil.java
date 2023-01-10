package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一ID生成器
 *
 * @author codejuzi
 */
@Component
public class UniqueIdGenerateUtil {

    /**
     * 开始时间时间戳（2022/7/24 0:0:0)
     */
    private static final Long BEGIN_TIMESTAMP = 1658620800L;

    /**
     * 时间戳位数
     */
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 生成全局唯一ID
     */
    public Long generateId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        String countKey = String.format("icr:%s:%s", keyPrefix, keySuffix);
        Long count = stringRedisTemplate.opsForValue().increment(countKey);

        return timestamp << COUNT_BITS | count;
    }
}
