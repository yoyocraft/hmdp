package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.constants.RedisConstants;
import com.hmdp.model.dto.Result;
import com.hmdp.model.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author codejuzi
 * 
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopById(Long id) {
        String shopCacheRedisKey = RedisConstants.CACHE_SHOP_KEY + id;
        // 查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopCacheRedisKey);
        if(StringUtils.isNotBlank(shopJson)) {
            // 命中，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // 如果是空值
        if("".equals(shopJson)) {
            return Result.fail("该店铺不存在");
        }

        // 未命中，查询数据库
        Shop shop = this.getById(id);
        if(shop == null) {
            // 店铺不存在时，缓存空值 => 解决缓存穿透
            stringRedisTemplate.opsForValue().set(shopCacheRedisKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        // 写入缓存，设置过期时间为30 + 随机值 分钟 => 解决缓存雪崩
        long cacheShopTTL = RedisConstants.CACHE_SHOP_TTL + RandomUtil.randomLong(5);
        stringRedisTemplate.opsForValue().set(shopCacheRedisKey, JSONUtil.toJsonStr(shop),
                cacheShopTTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    @Override
    // TODO: 2023/1/6 自定义异常
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        if(shop == null) {
            return Result.fail("店铺不存在");
        }
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("店铺 id 不能为空");
        }
        // 更新数据库，删除缓存，保证原子性
        this.updateById(shop);
        String shopCacheRedisKey = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(shopCacheRedisKey);
        return Result.ok();
    }
}
