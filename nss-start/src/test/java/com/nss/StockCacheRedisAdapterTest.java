package com.nss;

import com.nss.ddd.application.service.product.cache.StockCacheService;
import com.nss.ddd.infrastructure.cache.redis.StockCacheRedisAdapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StockCacheRedisAdapter} với {@link StringRedisTemplate} mock (backlog 0035 Phase 4) — kiểm
 * đủ ba mã trả về của cổng Lua, semantics {@code NX} của warm, và tính resilience (Redis chết không
 * ném exception ra ngoài, coding-conventions §11).
 */
@SuppressWarnings("unchecked")
class StockCacheRedisAdapterTest {

    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);

    private final StockCacheRedisAdapter adapter = new StockCacheRedisAdapter(stringRedisTemplate);

    @Test
    @DisplayName("Lua tra 1 -> DEDUCTED")
    void deductReturnsDeductedWhenScriptReturnsOne() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        int result = adapter.deductStock(9L, 2);

        assertEquals(StockCacheService.DEDUCTED, result);
    }

    @Test
    @DisplayName("Lua tra 0 -> INSUFFICIENT")
    void deductReturnsInsufficientWhenScriptReturnsZero() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        int result = adapter.deductStock(9L, 2);

        assertEquals(StockCacheService.INSUFFICIENT, result);
    }

    @Test
    @DisplayName("Lua tra -1 -> MISS")
    void deductReturnsMissWhenScriptReturnsMinusOne() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(-1L);

        int result = adapter.deductStock(9L, 2);

        assertEquals(StockCacheService.MISS, result);
    }

    @Test
    @DisplayName("Key Redis dung namespace Counter PRODUCT:{id}:STOCK")
    void deductUsesCounterNamespaceKey() {
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(List.of("PRODUCT:9:STOCK")), any()))
                .thenReturn(1L);

        int result = adapter.deductStock(9L, 2);

        assertEquals(StockCacheService.DEDUCTED, result);
        verify(stringRedisTemplate).execute(any(RedisScript.class), eq(List.of("PRODUCT:9:STOCK")), any());
    }

    @Test
    @DisplayName("Redis khong toi duoc khi deduct -> coi nhu MISS, KHONG nem exception")
    void deductSwallowsRedisFailureAsMiss() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("khong ket noi duoc"));

        int result = assertDoesNotThrow(() -> adapter.deductStock(9L, 2));

        assertEquals(StockCacheService.MISS, result);
    }

    @Test
    @DisplayName("warmIfAbsent goi SET...NX EX ttl (setIfAbsent), KHONG phai SET tran")
    void warmIfAbsentUsesSetIfAbsent() {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        adapter.warmIfAbsent(9L, 42L);

        verify(valueOperations).setIfAbsent(eq("PRODUCT:9:STOCK"), eq("42"), any(Duration.class));
        verify(valueOperations, never()).set(any(), any());
    }

    @Test
    @DisplayName("warmIfAbsent: Redis chet thi bo qua, KHONG nem exception")
    void warmIfAbsentSwallowsRedisFailure() {
        when(stringRedisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("khong ket noi duoc"));

        assertDoesNotThrow(() -> adapter.warmIfAbsent(9L, 42L));
    }

    @Test
    @DisplayName("increaseStock: Redis chet thi bo qua, KHONG nem exception")
    void increaseStockSwallowsRedisFailure() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("khong ket noi duoc"));

        assertDoesNotThrow(() -> adapter.increaseStock(9L, 2));

        verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any());
    }
}
