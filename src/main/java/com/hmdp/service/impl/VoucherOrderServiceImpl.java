package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.VoucherOrderConstants;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.model.dto.Result;
import com.hmdp.model.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UniqueIdGenerateUtil;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author codejuzi
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private UniqueIdGenerateUtil idGenerateUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 加载lua脚本
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("luascript/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 当前对象的代理对象
     */
    private IVoucherOrderService proxy;


    /**
     * 线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 内部类，异步处理订单业务
     */
    private class VoucherOrderHandler implements Runnable {

        private static final String MQ_GROUP_NAME = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    // 获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> mqList = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(MQ_GROUP_NAME, ReadOffset.lastConsumed())
                    );
                    // 校验
                    if (CollectionUtils.isEmpty(mqList)) {
                        // 没有消息，继续下一轮循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = mqList.get(0);
                    VoucherOrder voucherOrder
                            = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);
                    // 创建订单
                    proxy.createVoucherOrder(voucherOrder);
                    // 确认消息
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("订单处理异常", e);
                    handlerPendingList();
                }
            }
        }

        /**
         * 处理pending-list
         */
        private void handlerPendingList() {
            while (true) {
                try {
                    // 获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> mqList = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(MQ_GROUP_NAME, ReadOffset.from("0"))
                    );
                    // 校验
                    if (CollectionUtils.isEmpty(mqList)) {
                        // 没有异常处理的消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> mqRecord = mqList.get(0);
                    VoucherOrder voucherOrder
                            = BeanUtil.fillBeanWithMap(mqRecord.getValue(), new VoucherOrder(), true);
                    // 创建订单
                    proxy.createVoucherOrder(voucherOrder);
                    // 确认消息
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", mqRecord.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 生成订单id
        Long orderId = idGenerateUtil.generateId(VoucherOrderConstants.ORDER_ID_KEY_PREFIX);

        Long userId = UserHolder.getUser().getId();
        // 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );

        int res = Optional.ofNullable(result).orElse(1L).intValue();

        // 判断结果
        if (res != 0) {
            // 没有抢购资格
            return Result.fail(res == 1 ? "库存不足！" : "已经抢购过啦！");
        }

        return Result.ok(orderId);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 判断当前用户是否下过单，实现一人一单的逻辑
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 获取锁
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_VOUCHER_ORDER_KEY_PREFIX + userId);

        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            Integer count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count != null && count > 0) {
                log.error("不允许重复抢购");
                return;
            }

            // 扣减库存，使用乐观锁
            boolean isSuccess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();

            if (!isSuccess) {
                log.error("库存不足");
                return;
            }
            // 创建订单
            this.save(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

}
