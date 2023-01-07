package com.hmdp.service.impl;

import java.time.LocalDateTime;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.constants.RedisConstants;
import com.hmdp.model.dto.Result;
import com.hmdp.model.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisCacheClientUtil;
import com.hmdp.utils.RedisData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author codejuzi
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisCacheClientUtil cacheClientUtil;

    @Override
    public Result queryShopById(Long id) {

        // 基于逻辑过期时间解决缓存击穿
        Shop shop = cacheClientUtil.queryShopWithLogicalExpireTime(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
                this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES,RedisConstants.LOCK_SHOP_KEY);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺 id 不能为空");
        }
        // 更新数据库，删除缓存，保证原子性
        this.updateById(shop);
        String shopCacheRedisKey = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(shopCacheRedisKey);
        return Result.ok();
    }


    /**
     * 封装逻辑过期时间到店铺信息，存储在redis中
     *
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = this.getById(id);

        // 封装逻辑过期时间
        RedisData shopRedisData = new RedisData();
        shopRedisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        shopRedisData.setData(shop);
        String shopCacheRedisKey = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.opsForValue().set(shopCacheRedisKey, JSONUtil.toJsonStr(shopRedisData));
    }
}
