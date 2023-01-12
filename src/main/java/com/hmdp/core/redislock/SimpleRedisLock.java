package com.hmdp.core.redislock;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * ILock 简单实现类
 *
 * @author codejuzi
 */
public class SimpleRedisLock implements ILock {

    /**
     * 锁名称
     */
    private final String lockName;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 锁前缀
     */
    private static final String LOCK_KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";


    public SimpleRedisLock(String lockName, StringRedisTemplate stringRedisTemplate) {
        this.lockName = lockName;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取当前线程标识
        long threadId = Thread.currentThread().getId();
        String lockKey = LOCK_KEY_PREFIX + lockName;
        String lockId = ID_PREFIX + threadId;
        // 获取锁
        Boolean success
                = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        String lockKey = LOCK_KEY_PREFIX + lockName;
        // 获取当前线程标识
        long threadId = Thread.currentThread().getId();
        String lockId = ID_PREFIX + threadId;
        // 获取锁中的标识
        String lockStoreId = stringRedisTemplate.opsForValue().get(lockKey);
        if(Objects.equals(lockStoreId, lockId)) {
            // 当前锁是自己的，释放锁
            stringRedisTemplate.delete(lockKey);
        }
    }
}
