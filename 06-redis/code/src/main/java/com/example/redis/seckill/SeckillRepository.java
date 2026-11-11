package com.example.redis.seckill;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SeckillRepository {

    private final JdbcTemplate jdbc;

    public SeckillRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int stock(long voucherId) {
        Integer stock = jdbc.queryForObject("SELECT stock FROM tb_seckill_voucher WHERE voucher_id = ?", Integer.class, voucherId);
        return stock == null ? 0 : stock;
    }

    public void resetStock(long voucherId, int stock) {
        jdbc.update("UPDATE tb_seckill_voucher SET stock = ? WHERE voucher_id = ?", stock, voucherId);
        jdbc.update("DELETE FROM tb_voucher_order WHERE voucher_id = ?", voucherId);
    }

    /** WHERE stock > 0：乐观锁，防止超卖的最后一道防线 */
    public boolean deductStock(long voucherId) {
        return jdbc.update("UPDATE tb_seckill_voucher SET stock = stock - 1 WHERE voucher_id = ? AND stock > 0", voucherId) > 0;
    }

    public int orderCount(long userId, long voucherId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_voucher_order WHERE user_id = ? AND voucher_id = ?",
                Integer.class, userId, voucherId);
        return count == null ? 0 : count;
    }

    public void saveOrder(VoucherOrder order) {
        jdbc.update("INSERT INTO tb_voucher_order (id, user_id, voucher_id) VALUES (?, ?, ?)",
                order.getId(), order.getUserId(), order.getVoucherId());
    }

    public int orderTotal(long voucherId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id = ?", Integer.class, voucherId);
        return count == null ? 0 : count;
    }
}
