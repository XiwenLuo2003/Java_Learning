-- 用 ZSet 给 Hash 的字段设置过期时间（Redis 7.4 之前 Hash 只能整个 key 过期）
-- 数据存在 Hash：KEYS[1]；每个字段的过期时间存在 ZSet：KEYS[2]，member = 字段名，score = 过期时刻（毫秒）
-- ARGV[1] 操作：set / get / clean
-- 三个分支都返回字符串（或 nil），Java 端统一按 String 接收
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local op = ARGV[1]

if op == 'set' then
    -- ARGV[2] 字段，ARGV[3] 值，ARGV[4] 存活毫秒数
    redis.call('HSET', KEYS[1], ARGV[2], ARGV[3])
    redis.call('ZADD', KEYS[2], now + tonumber(ARGV[4]), ARGV[2])
    return 'OK'
end

if op == 'get' then
    -- 惰性删除：读的时候发现过期了，顺手删掉，返回 nil
    local expireAt = redis.call('ZSCORE', KEYS[2], ARGV[2])
    if expireAt and tonumber(expireAt) <= now then
        redis.call('HDEL', KEYS[1], ARGV[2])
        redis.call('ZREM', KEYS[2], ARGV[2])
        return nil
    end
    return redis.call('HGET', KEYS[1], ARGV[2])
end

if op == 'clean' then
    -- 定期删除：由定时任务调用，ZRANGEBYSCORE 一次找出所有已过期的字段
    local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now)
    if #expired > 0 then
        redis.call('HDEL', KEYS[1], unpack(expired))
        redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
    end
    return tostring(#expired)
end

return redis.error_reply('unknown op: ' .. tostring(op))
