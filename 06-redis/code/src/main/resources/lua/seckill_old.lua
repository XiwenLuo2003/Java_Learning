-- 照搬黑马点评 resources/seckill_old.lua：扣库存、记一人一单，但不发消息
-- 黑马点评当前加载的就是这个脚本，所以 Redis 库存扣了、数据库却没有订单
local voucherId = ARGV[1]
local userId = ARGV[2]

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
return 0
