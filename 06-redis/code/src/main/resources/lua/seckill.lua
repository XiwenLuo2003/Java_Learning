-- 照搬黑马点评 resources/seckill.lua
-- ARGV[1] 优惠券 id，ARGV[2] 用户 id，ARGV[3] 订单 id
-- 返回 0 成功，1 库存不足，2 重复下单
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
-- 发到 Stream：消费者用 XREADGROUP 读。* 表示由 Redis 生成消息 id
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0
