package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.constants.RedisConstants;
import com.hmdp.model.dto.Result;
import com.hmdp.model.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author codejuzi
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 查询缓存
        String shopTypeCacheRedisKey = RedisConstants.CACHE_SHOP_TYPE_KEY;
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(shopTypeCacheRedisKey);
        if (StringUtils.isNotBlank(shopTypeListJson)) {
            // 命中
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        // 未命中
        List<ShopType> shopTypeList = this.query().orderByAsc("sort").list();
        // 写入缓存，设置超时时间为1天
        stringRedisTemplate.opsForValue().set(shopTypeCacheRedisKey, JSONUtil.toJsonStr(shopTypeList)
                , RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return Result.ok(shopTypeList);
    }
}
