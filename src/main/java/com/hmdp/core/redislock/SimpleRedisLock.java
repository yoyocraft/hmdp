package com.hmdp.core.redislock;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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

    /**
     * 加载lua脚本
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lockluascript/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


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
        // 调用Lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_KEY_PREFIX + lockName),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
}
