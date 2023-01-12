package com.hmdp.core.redislock;

/**
 * 锁接口，基于Redis
 *
 * @author codejuzi
 */
public interface ILock {


    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true：获取锁成功，false：获取锁失败
     */
    boolean tryLock(long timeoutSec);


    /**
     * 释放锁
     */
    void unlock();

}
