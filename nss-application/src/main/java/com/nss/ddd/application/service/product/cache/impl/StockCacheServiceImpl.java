package com.nss.ddd.application.service.product.cache.impl;

import com.nss.ddd.application.service.product.cache.StockCacheService;
import com.nss.ddd.infrastructure.cache.redis.StockCacheRedisAdapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

/**
 * Hiện thực {@link StockCacheService} — orchestrate warm-on-miss + retry đúng một lần, phần còn lại
 * (Lua atomic gate) dời hẳn cho {@link StockCacheRedisAdapter} (backlog 0035 Phase 2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheServiceImpl implements StockCacheService {

    private final StockCacheRedisAdapter stockCacheRedisAdapter;

    /**
     * {@inheritDoc}
     * <p>
     * <b>Đúng một lần retry, không đệ quy, không vòng lặp.</b> Miss ngay cả sau khi warm (ví dụ Redis
     * chết giữa hai lời gọi) trả thẳng {@link #MISS} lần hai — phía gọi ({@code OrderAppServiceImpl})
     * để Tầng 2 MySQL quyết định, không tự ý coi đây là hết hàng.
     */
    @Override
    public int deductStock(Long productId, int quantity, long knownStock) {
        int result = stockCacheRedisAdapter.deductStock(productId, quantity);
        if (result == MISS) {
            log.warn("deductStock: cache miss, warm roi retry DUNG 1 LAN | productId={} knownStock={}",
                    productId, knownStock);
            stockCacheRedisAdapter.warmIfAbsent(productId, knownStock);
            result = stockCacheRedisAdapter.deductStock(productId, quantity);
        }
        return result;
    }

    @Override
    public void increaseStock(Long productId, int quantity) {
        stockCacheRedisAdapter.increaseStock(productId, quantity);
    }
}
