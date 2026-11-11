package com.example.redis.lock;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 自研分布式锁：SET key token NX EX + Lua 释放。
 * <p>
 * 和黑马点评 utils/SimpleRedisLock 的区别：黑马用“UUID + 线程 id”当标识，释放时要求必须是同一个线程；
 * 这里改成 tryLock 返回 token、unlock 时把 token 传回来。好处是加锁和解锁可以在不同线程
 * （比如逻辑过期里，主线程加锁、线程池里的重建任务解锁），本质上“谁持有锁”就是“谁拿着 token”。
 * <p>
 * 这把锁的局限（也是 Redisson 要解决的）：不可重入；业务执行时间超过 ttl 锁会提前过期；拿不到锁不会排队等待。
 */
@Component
public class SimpleRedisLock {

    private static final String KEY_PREFIX = "lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();

    static {
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;

    public SimpleRedisLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * SET lock:{name} {token} NX EX {ttl}：一条命令同时完成“不存在才设置”和“设置过期时间”。
     * 如果拆成 SETNX + EXPIRE 两条，中间进程挂了，锁就永远不会过期。
     *
     * @return 加锁成功返回 token，失败返回 null
     */
    public String tryLock(String name, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(KEY_PREFIX + name, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    /** 只删自己的锁：GET 比较 + DEL 放在一个 Lua 脚本里原子执行 */
    public boolean unlock(String name, String token) {
        Long result = redis.execute(UNLOCK_SCRIPT, List.of(KEY_PREFIX + name), token);
        return result != null && result == 1L;
    }

    /** 错误示范：不管锁是谁的，直接删 */
    public void unlockUnsafe(String name) {
        redis.delete(KEY_PREFIX + name);
    }

    /**
     * 错误示范：先 GET 比较、再 DEL，两步不是原子的（黑马点评 SimpleRedisLock 里注释掉的那版）。
     * GET 判断完是自己的锁，这时线程卡住（GC 停顿），锁过期被别人抢到，醒来 DEL 删掉的就是别人的锁。
     * pauseMillis 用来模拟这段停顿。
     */
    public boolean unlockCheckThenDelete(String name, String token, long pauseMillis) throws InterruptedException {
        if (token.equals(redis.opsForValue().get(KEY_PREFIX + name))) {
            Thread.sleep(pauseMillis);
            redis.delete(KEY_PREFIX + name);
            return true;
        }
        return false;
    }
}
