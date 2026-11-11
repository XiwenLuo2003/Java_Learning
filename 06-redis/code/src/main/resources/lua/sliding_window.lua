-- ZSet 滑动窗口限流（java.md 第 151 行的方案）
-- KEYS[1] 限流 key，例如 limit:user:{uid}:{api}
-- ARGV[1] 窗口长度（毫秒），ARGV[2] 窗口内最多允许的请求数，ARGV[3] 本次请求的唯一 ID（作为 member）
-- 返回 1 放行，0 拒绝

-- 用 Redis 服务器的时间，而不是各个应用实例自己的时间：多台机器时钟不一致时，窗口仍然是准的
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])

-- ① 删掉窗口外的旧记录：score <= now - window
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
-- ② 统计窗口内的请求数
local count = redis.call('ZCARD', KEYS[1])
if count < limit then
    -- ③ 没超限：记录本次请求。member 必须唯一，如果用时间戳当 member，同一毫秒的请求会互相覆盖
    redis.call('ZADD', KEYS[1], now, ARGV[3])
    -- 整个窗口都没有请求时，key 自动过期，不占内存
    redis.call('PEXPIRE', KEYS[1], window)
    return 1
end
return 0
