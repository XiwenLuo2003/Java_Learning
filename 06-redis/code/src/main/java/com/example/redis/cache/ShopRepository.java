package com.example.redis.cache;

import com.example.redis.common.Concurrent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 店铺表的数据访问。queryCount 统计“打到数据库的查询次数”，是缓存实验的核心观测指标；
 * slowMillis 模拟慢查询（缓存重建越慢，击穿越严重）。
 */
@Repository
public class ShopRepository {

    private final JdbcTemplate jdbc;
    private final AtomicInteger queryCount = new AtomicInteger();
    private volatile long slowMillis;

    public ShopRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Shop findById(long id) {
        queryCount.incrementAndGet();
        if (slowMillis > 0) {
            Concurrent.sleep(slowMillis);
        }
        List<Shop> list = jdbc.query("SELECT id, name, score FROM tb_shop WHERE id = ?",
                (rs, i) -> new Shop(rs.getLong("id"), rs.getString("name"), rs.getInt("score")), id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Long> findAllIds() {
        return jdbc.queryForList("SELECT id FROM tb_shop", Long.class);
    }

    public int updateScore(long id, int score) {
        return jdbc.update("UPDATE tb_shop SET score = ? WHERE id = ?", score, id);
    }

    public int updateName(long id, String name) {
        return jdbc.update("UPDATE tb_shop SET name = ? WHERE id = ?", name, id);
    }

    public int resetQueryCount() {
        return queryCount.getAndSet(0);
    }

    public int queryCount() {
        return queryCount.get();
    }

    public void setSlowMillis(long slowMillis) {
        this.slowMillis = slowMillis;
    }
}
