package com.hmdp.service;

import com.hmdp.model.dto.Result;
import com.hmdp.model.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author codejuzi
 */
public interface IShopService extends IService<Shop> {


    /**
     * 根据id查询店铺信息
     *
     * @param id
     * @return
     */
    Result queryShopById(Long id);

    /**
     * 更新店铺信息
     *
     * @param shop
     * @return
     */
    Result updateShop(Shop shop);
}
