package com.example.mq.reliable;

import java.time.Duration;

/**
 * 用 Redis SETNX 做消费去重的写法。本项目不连接 Redis，去重跑的是 {@link OrderService#consume} 的唯一索引。
 * <pre>
 * // Boolean ok = redis.opsForValue().setIfAbsent("idem:" + msgId, "1", Duration.ofDays(1));
 * // if (!Boolean.TRUE.equals(ok)) return; // 已经消费过
 * // doBusiness();
 * </pre>
 * 和数据库唯一索引的差别：SETNX 和业务不在一个事务里。业务执行完、进程在这之前崩溃，key 已经占上，消息不会再被处理；
 * key 过期之后，同一条消息又会被当成新的。所以 java.md 把“唯一索引去重表”放在前面，SETNX 适合可以接受少量误差的拦截。
 * {@link Duration} 只是为了让这段伪代码里的类型在本文件里真实存在，读的时候以注释为准。
 */
public final class SetnxIdempotent {

    private SetnxIdempotent() {
    }

    public static Duration ttl() {
        return Duration.ofDays(1);
    }
}
