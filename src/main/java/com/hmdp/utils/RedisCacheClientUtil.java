package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.constants.RedisConstants;
import com.hmdp.model.dto.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Redis 缓存工具类
 *
 * @author codejuzi
 */
@Slf4j
@Component
public class RedisCacheClientUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 缓存重建线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 向缓存中添加key
     */
    public void set(String key, Object value, Long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout, unit);
    }

    /**
     * 向缓存中添加key，设置逻辑过期时间
     */
    private void setWithLogicalExpireTime(String key, Object value, Long timeout, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(timeout)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存雪崩
     *
     * @param type 返回值字节码对象
     * @param dbFallback 查询数据库的操作
     * @param <T> id类型
     * @param <R> 返回值类型
     * @return R
     */
    public <T, R> R queryShopWithPassThrough(
            String keyPrefix, T id, Class<R> type, Function<T, R> dbFallback, Long timeout, TimeUnit unit) {
        String cacheRedisKey = keyPrefix + id;
        // 查询缓存
        String json = stringRedisTemplate.opsForValue().get(cacheRedisKey);
        if (StringUtils.isNotBlank(json)) {
            // 命中，直接返回
            return JSONUtil.toBean(json, type);
        }

        // 如果是空值
        if ("".equals(json)) {
            return null;
        }

        // 未命中，查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 店铺不存在时，缓存空值 => 解决缓存穿透
            stringRedisTemplate.opsForValue().set(cacheRedisKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 写入缓存，设置过期时间为30 + 随机值 分钟 => 解决缓存雪崩
        long cacheTimeout = timeout + RandomUtil.randomLong(5);
        this.set(cacheRedisKey, r, cacheTimeout, unit);
        return r;
    }

    /**
     * 使用逻辑过期解决缓存击穿
     *
     * @param type 返回值字节码对象
     * @param dbFallback 查询数据库的操作
     * @param <T> id类型
     * @param <R> 返回值类型
     * @return R
     */
    public <T, R> R queryShopWithLogicalExpireTime(
            String keyPrefix, T id, Class<R> type, Function<T, R> dbFallback, Long timeout, TimeUnit unit,
            String lockKeyPrefix
    ) {
        String cacheRedisKey = keyPrefix + id;
        // 查询缓存
        String json = stringRedisTemplate.opsForValue().get(cacheRedisKey);
        if (StringUtils.isBlank(json)) {
            // 不命中，直接返回
            return null;
        }
        // 存在，反序列化
        RedisData shopCacheRedisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = shopCacheRedisData.getExpireTime();
        JSONObject shopJsonObj = (JSONObject) shopCacheRedisData.getData();
        R r = JSONUtil.toBean(shopJsonObj, type);

        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        // 过期，缓存重建
        String cacheLockKey = lockKeyPrefix + id;
        boolean isLock = tryLock(cacheLockKey);
        if (isLock) {
            // 获取成功，再次获取缓存，DoubleCheck
            json = stringRedisTemplate.opsForValue().get(cacheLockKey);
            if (StringUtils.isNotBlank(json)) {
                // 命中，直接返回
                return JSONUtil.toBean(json, type);
            }
            // 开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 缓存重建
                try {
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    long cacheTimeout = timeout + RandomUtil.randomLong(5);
                    this.setWithLogicalExpireTime(keyPrefix, id, cacheTimeout, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    this.unLock(cacheLockKey);
                }
            });
        }
        return r;
    }

    /**
     * 使用互斥锁解决缓存击穿
     *
     * @param type 返回值字节码对象
     * @param dbFallback 查询数据库的操作
     * @param <T> id类型
     * @param <R> 返回值类型
     * @return R
     */
    public <T, R> R queryShopWithMutex(
            String keyPrefix, T id, Class<R> type, Function<T, R> dbFallback, Long timeout, TimeUnit unit, String lockKeyPrefix) {
        String cacheKey = keyPrefix + id;
        // 查询缓存
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(json)) {
            // 命中，直接返回
            return JSONUtil.toBean(json, type);
        }

        // 如果是空值
        if ("".equals(json)) {
            return null;
        }

        // 获取互斥锁
        String lockKey = lockKeyPrefix + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 获取失败，重新获取
            if (!isLock) {
                Thread.sleep(30);
                return queryShopWithMutex(keyPrefix, id, type, dbFallback, timeout, unit, lockKeyPrefix);
            }
            // 获取成功，再次获取缓存，DoubleCheck
            json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.isNotBlank(json)) {
                // 命中，直接返回
                return JSONUtil.toBean(json, type);
            }
            // 不命中，再重建缓存
            r = dbFallback.apply(id);
            // 校验
            if (r == null) {
                // 缓存空值
                stringRedisTemplate.opsForValue()
                        .set(cacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 存在，写入缓存
            long cacheTimeout = timeout + RandomUtil.randomLong(5);
            this.set(cacheKey, r, cacheTimeout, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unLock(lockKey);
        }
        return r;
    }


    private boolean tryLock(String lockKey) {
        Boolean flag
                = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

}
