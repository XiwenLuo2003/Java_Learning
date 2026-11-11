-- 照搬黑马点评 resources/unlock.lua
-- KEYS[1] 锁的 key，ARGV[1] 加锁时写进去的 token
-- “比较 token”和“删除”必须在一个脚本里：Redis 执行脚本期间不会插入别的命令
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0
