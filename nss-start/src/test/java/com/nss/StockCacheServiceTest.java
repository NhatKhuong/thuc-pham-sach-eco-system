package com.nss;

import com.nss.ddd.application.service.product.cache.StockCacheService;
import com.nss.ddd.application.service.product.cache.impl.StockCacheServiceImpl;
import com.nss.ddd.infrastructure.cache.redis.StockCacheRedisAdapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract Lua qua mock {@link StockCacheRedisAdapter} (backlog 0035 Phase 4) — {@code deductStock}
 * phải warm-on-miss và retry <b>đúng một lần</b>, không lặp thêm lần nữa dù lần retry vẫn miss.
 */
class StockCacheServiceTest {

    private final StockCacheRedisAdapter adapter = mock(StockCacheRedisAdapter.class);

    private final StockCacheService stockCacheService = new StockCacheServiceImpl(adapter);

    @Test
    @DisplayName("deduct thanh cong ngay lan dau -> khong warm, khong retry")
    void deductSucceedsOnFirstTry() {
        when(adapter.deductStock(1L, 2)).thenReturn(StockCacheService.DEDUCTED);

        int result = stockCacheService.deductStock(1L, 2, 10L);

        assertEquals(StockCacheService.DEDUCTED, result);
        verify(adapter, times(1)).deductStock(1L, 2);
        verify(adapter, never()).warmIfAbsent(eq(1L), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("khong du kho ngay lan dau (0) -> khong warm, khong retry, tra thang INSUFFICIENT")
    void insufficientOnFirstTryDoesNotWarmOrRetry() {
        when(adapter.deductStock(1L, 2)).thenReturn(StockCacheService.INSUFFICIENT);

        int result = stockCacheService.deductStock(1L, 2, 10L);

        assertEquals(StockCacheService.INSUFFICIENT, result);
        verify(adapter, times(1)).deductStock(1L, 2);
        verify(adapter, never()).warmIfAbsent(eq(1L), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("mien lan dau, thanh cong sau khi warm -> warm dung 1 lan, deduct dung 2 lan")
    void missThenWarmThenSucceeds() {
        when(adapter.deductStock(1L, 2))
                .thenReturn(StockCacheService.MISS)
                .thenReturn(StockCacheService.DEDUCTED);

        int result = stockCacheService.deductStock(1L, 2, 10L);

        assertEquals(StockCacheService.DEDUCTED, result);
        verify(adapter, times(1)).warmIfAbsent(1L, 10L);
        verify(adapter, times(2)).deductStock(1L, 2);
    }

    @Test
    @DisplayName("mien CA HAI lan -> tra MISS, KHONG co lan retry thu hai (warm/deduct dung 1/2 lan)")
    void missTwiceDoesNotRetryAgain() {
        when(adapter.deductStock(1L, 2)).thenReturn(StockCacheService.MISS);

        int result = stockCacheService.deductStock(1L, 2, 10L);

        assertEquals(StockCacheService.MISS, result);
        verify(adapter, times(1)).warmIfAbsent(1L, 10L);
        verify(adapter, times(2)).deductStock(1L, 2);
    }

    @Test
    @DisplayName("increaseStock uy thac thang cho adapter")
    void increaseStockDelegatesToAdapter() {
        stockCacheService.increaseStock(1L, 3);

        verify(adapter, times(1)).increaseStock(1L, 3);
    }
}
