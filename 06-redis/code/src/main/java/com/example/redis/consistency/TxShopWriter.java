package com.example.redis.consistency;

import com.example.redis.cache.ShopRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务里删缓存的两种写法。单独放一个 Bean，是为了让 @Transactional 走代理生效（05-springboot 第 10 课）。
 * beforeCommit 用来模拟“删完缓存后、事务提交前，事务里还有别的操作”，给并发读留出窗口。
 */
@Service
public class TxShopWriter {

    private final ShopRepository shopRepository;
    private final StringRedisTemplate redis;

    public TxShopWriter(ShopRepository shopRepository, StringRedisTemplate redis) {
        this.shopRepository = shopRepository;
        this.redis = redis;
    }

    /** 黑马点评 ShopServiceImpl#update 的写法：事务里更新数据库，紧接着删缓存，此时事务还没提交 */
    @Transactional
    public void updateThenDeleteInsideTx(long id, int score, String cacheKey, Runnable beforeCommit) {
        shopRepository.updateScore(id, score);
        redis.delete(cacheKey);
        beforeCommit.run();
    }

    /** 正确写法：注册事务同步回调，事务提交成功后再删缓存；事务回滚则不删 */
    @Transactional
    public void updateThenDeleteAfterCommit(long id, int score, String cacheKey, Runnable beforeCommit) {
        shopRepository.updateScore(id, score);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redis.delete(cacheKey);
            }
        });
        beforeCommit.run();
    }
}
