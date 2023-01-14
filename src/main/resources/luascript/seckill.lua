-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 订单id
local orderId = ARGV[3]

-- 库存key
local stockKey = "seckill:stock:" .. voucherId
-- 订单key
local orderKey = "seckill:order:" .. userId

-- 判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

-- 判断用户是否下过单
if(redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 扣减库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 下单（保存用户） sadd orderKey userId
redis.call('sadd', orderKey, userId)
-- 发送信息到消息队列 XADD stream.orders * k1 v1 k2 v2
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

return 0
