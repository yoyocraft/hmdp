package com.hmdp.service.impl;

import com.hmdp.model.dto.Result;
import com.hmdp.model.entity.SeckillVoucher;
import com.hmdp.model.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UniqueIdGenerateUtil;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author codejuzi
 * 
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private UniqueIdGenerateUtil idGenerateUtil;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取秒杀券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 判断活动是否开始
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动尚未开始");
        }
        // 判断活动是否结束
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已经结束");
        }

        // 判断库存是否充足
        if(seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            // 获取当前对象代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(Long voucherId) {
        // 判断当前用户是否下过单，实现一人一单的逻辑
        Long userId = UserHolder.getUser().getId();
        Integer count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count != null && count > 0) {
            return Result.fail("已经抢购过啦！");
        }

        // 扣减库存，使用乐观锁
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if(!isSuccess) {
            return Result.fail("库存不足！");
        }
        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // TODO: 2023/1/10 提取常量值
        Long orderId = idGenerateUtil.generateId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        this.save(voucherOrder);

        return Result.ok(orderId);
    }
}
