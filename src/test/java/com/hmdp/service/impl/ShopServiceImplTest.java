package com.hmdp.service.impl;

import com.hmdp.constants.RedisConstants;
import com.hmdp.model.entity.Shop;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class ShopServiceImplTest {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void saveShop2Redis() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void loadShopData() {
        // 查询店铺信息
        List<Shop> shopList = shopService.list();
        // 按照typeId分组
        Map<Long, List<Shop>> typeShopMap = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 分批次写入Redis
        for (Map.Entry<Long, List<Shop>> entry : typeShopMap.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shops = entry.getValue();

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(RedisConstants.SHOP_GEO_KEY + typeId, locations);
        }
    }
}