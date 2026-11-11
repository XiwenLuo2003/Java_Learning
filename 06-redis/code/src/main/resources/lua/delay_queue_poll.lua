-- ZSet 延迟队列：score = 任务的执行时刻（毫秒），取出一个已经到期的任务
-- KEYS[1] 队列 key，ARGV[1] 当前时间（毫秒，和入队时算 score 用同一个时钟）
-- “查到期任务”和“删除”必须原子：否则两个消费者同时 ZRANGEBYSCORE 拿到同一个任务，会重复执行
local tasks = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, 1)
if #tasks == 0 then
    return nil
end
redis.call('ZREM', KEYS[1], tasks[1])
return tasks[1]
