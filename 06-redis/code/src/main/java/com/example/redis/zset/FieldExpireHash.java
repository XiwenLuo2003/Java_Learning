package com.example.redis.zset;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 用 ZSet 模拟 Hash 字段级过期（小红书一二面连环考）。
 * <p>
 * 场景：用户的优惠券包 Hash，field = 券 ID，value = 券信息，每张券的有效期不同。
 * Hash 的 EXPIRE 只能作用于整个 key，所以另建一个 ZSet 记录“字段 → 过期时刻”：
 * <ul>
 *   <li>读时检查（惰性删除）：get 时发现过期就删，对调用方表现为“不存在”</li>
 *   <li>定时扫描（定期删除）：clean 用 ZRANGEBYSCORE 一次找出所有过期字段批量删，防止没人读的过期字段一直占内存</li>
 * </ul>
 * 这和 Redis 自己处理过期 key 的策略（惰性删除 + 定期删除）一模一样。Redis 7.4 起原生支持 HEXPIRE。
 * <p>
 * 两个 key 用了同一个 hash tag {...}：集群模式下 Lua 脚本操作的所有 key 必须在同一个槽，否则报 CROSSSLOT。
 */
@Component
public class FieldExpireHash {

    private static final DefaultRedisScript<String> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setLocation(new ClassPathResource("lua/hash_field_expire.lua"));
        SCRIPT.setResultType(String.class);
    }

    private final StringRedisTemplate redis;

    public FieldExpireHash(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public static String hashKey(String name) {
        return "{" + name + "}";
    }

    public static String expireKey(String name) {
        return "{" + name + "}:expire";
    }

    public void set(String name, String field, String value, Duration ttl) {
        redis.execute(SCRIPT, List.of(hashKey(name), expireKey(name)), "set", field, value, String.valueOf(ttl.toMillis()));
    }

    public String get(String name, String field) {
        return redis.execute(SCRIPT, List.of(hashKey(name), expireKey(name)), "get", field);
    }

    public long clean(String name) {
        String removed = redis.execute(SCRIPT, List.of(hashKey(name), expireKey(name)), "clean");
        return removed == null ? 0 : Long.parseLong(removed);
    }
}
