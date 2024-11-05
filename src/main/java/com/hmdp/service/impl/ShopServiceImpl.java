package com.hmdp.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.SystemConstants;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.model.dto.RedisData;
import com.hmdp.model.dto.Result;
import com.hmdp.model.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisCacheClientUtil;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
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
        Shop shop = cacheClientUtil.queryShopWithLogicalExpireTime(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES,
                RedisConstants.LOCK_SHOP_KEY
        );

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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否要根据坐标查询
        if (x == null || y == null) {
            // 不需要根据坐标查询，根据类型分页查询
            Page<Shop> shopPage = this.query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(shopPage.getRecords());
        }
        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String distanceKey = RedisConstants.SHOP_GEO_KEY + typeId;
        // 查询Redis，按照距离排序，分页,结果 shopId, distance
        // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        distanceKey,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 解析出shopId
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> resultList = results.getContent();
        if (resultList.size() < from) {
            // 已经是最后一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 截取from ~ end 的部分
        List<Long> shopIdList = new ArrayList<>(resultList.size());
        Map<String, Distance> distanceMap = new HashMap<>(resultList.size());
        resultList.stream().skip(from).forEach(result -> {
            // 获取shopId
            String shopIdStr = result.getContent().getName();
            shopIdList.add(Long.valueOf(shopIdStr));
            // 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 根据id查询店铺
        String shopIdListStr = CharSequenceUtil.join(",", shopIdList);
        List<Shop> shopList
                = this.query().in("id", shopIdList)
                .last("order by field(id, " + shopIdListStr + ")").list();
        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        return Result.ok(shopList);
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
