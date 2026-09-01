package com.nss;

import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.application.service.product.cache.impl.ProductCacheServiceImpl;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.service.ProductDomainService;
import com.nss.ddd.infrastructure.cache.redis.ProductCacheRedisAdapter;
import com.nss.ddd.infrastructure.distributed.redisson.DistributedLockService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductCacheServiceImpl} với mọi phụ thuộc mock (backlog 0035 Phase 4) — kiểm từng tầng của
 * cache 3 tầng (architecture §4): hit Guava, hit Redis, miss cả hai (lấy lock, đọc DB), fallback khi
 * không lấy được lock, và evict.
 */
@SuppressWarnings("unchecked")
class ProductCacheServiceImplTest {

    private final ProductDomainService productDomainService = mock(ProductDomainService.class);

    private final ProductCacheRedisAdapter productCacheRedisAdapter = mock(ProductCacheRedisAdapter.class);

    private final DistributedLockService distributedLockService = mock(DistributedLockService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProductCacheServiceImpl productCacheService = new ProductCacheServiceImpl(
            productDomainService, productCacheRedisAdapter, distributedLockService, objectMapper);

    private static Product genProduct() {
        return new Product().setId(1L).setSlug("ca-rot").setName("Cà rốt").setPrice(20_000L)
                .setUnit("kg").setStock(10).setSold(0).setRating(BigDecimal.ZERO).setReviewCount(0)
                .setIsFeatured(false).setIsBestSeller(false).setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Ca Guava lan Redis mien -> lay lock, doc DB, nap lai ca hai tang")
    void bothTiersMissLoadsFromDbUnderLock() {
        Product product = genProduct();
        when(productDomainService.findBySlug("ca-rot")).thenReturn(product);
        when(productDomainService.findImages(1L)).thenReturn(List.of());
        when(productCacheRedisAdapter.get(any())).thenReturn(null);
        simulateLockAcquired();

        ProductResponse result = productCacheService.getProductBySlug("ca-rot");

        assertNotNull(result);
        assertEquals("ca-rot", result.getSlug());
        verify(productDomainService, times(1)).findBySlug("ca-rot");
        verify(productCacheRedisAdapter).set(eq("PRODUCT:ITEM:ca-rot"), any(), any());
    }

    @Test
    @DisplayName("Guava hit o lan goi thu hai -> khong doc DB lai, khong can lock lai")
    void guavaHitOnSecondCallSkipsEverything() {
        Product product = genProduct();
        when(productDomainService.findBySlug("ca-rot")).thenReturn(product);
        when(productDomainService.findImages(1L)).thenReturn(List.of());
        when(productCacheRedisAdapter.get(any())).thenReturn(null);
        simulateLockAcquired();

        productCacheService.getProductBySlug("ca-rot");
        ProductResponse second = productCacheService.getProductBySlug("ca-rot");

        assertEquals("ca-rot", second.getSlug());
        verify(productDomainService, times(1)).findBySlug("ca-rot");
        verify(distributedLockService, times(1)).executeWithLock(any(), any(), any());
    }

    @Test
    @DisplayName("Redis hit -> khong doc DB, khong can lock")
    void redisHitSkipsDbAndLock() throws Exception {
        ProductResponse cached = new ProductResponse().setId(1L).setSlug("ca-rot").setName("Cà rốt");
        when(productCacheRedisAdapter.get("PRODUCT:ITEM:ca-rot"))
                .thenReturn(objectMapper.writeValueAsString(cached));

        ProductResponse result = productCacheService.getProductBySlug("ca-rot");

        assertEquals("ca-rot", result.getSlug());
        verify(productDomainService, never()).findBySlug(any());
        verify(distributedLockService, never()).executeWithLock(any(), any(), any());
    }

    @Test
    @DisplayName("Khong lay duoc lock -> fallback doc DB khong khoa, van tra dung ket qua")
    void lockNotAcquiredFallsBackToUnlockedRead() {
        Product product = genProduct();
        when(productDomainService.findBySlug("ca-rot")).thenReturn(product);
        when(productDomainService.findImages(1L)).thenReturn(List.of());
        when(productCacheRedisAdapter.get(any())).thenReturn(null);
        simulateLockNotAcquired();

        ProductResponse result = productCacheService.getProductBySlug("ca-rot");

        assertNotNull(result);
        assertEquals("ca-rot", result.getSlug());
        verify(productDomainService, times(1)).findBySlug("ca-rot");
    }

    @Test
    @DisplayName("San pham khong ton tai -> null, KHONG cache ket qua rong")
    void productNotFoundReturnsNullAndDoesNotCache() {
        when(productDomainService.findBySlug("khong-ton-tai")).thenReturn(null);
        when(productCacheRedisAdapter.get(any())).thenReturn(null);
        simulateLockAcquired();

        ProductResponse result = productCacheService.getProductBySlug("khong-ton-tai");

        assertNull(result);
        verify(productCacheRedisAdapter, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("evict xoa ca Guava lan Redis — lan doc ke tiep phai doc lai DB")
    void evictClearsBothTiers() {
        Product product = genProduct();
        when(productDomainService.findBySlug("ca-rot")).thenReturn(product);
        when(productDomainService.findImages(1L)).thenReturn(List.of());
        when(productCacheRedisAdapter.get(any())).thenReturn(null);
        simulateLockAcquired();
        productCacheService.getProductBySlug("ca-rot");

        productCacheService.evict("ca-rot");
        productCacheService.getProductBySlug("ca-rot");

        verify(productCacheRedisAdapter).evict("PRODUCT:ITEM:ca-rot");
        verify(productDomainService, times(2)).findBySlug("ca-rot");
    }

    private void simulateLockAcquired() {
        when(distributedLockService.executeWithLock(any(), any(), any())).thenAnswer(invocation -> {
            Supplier<ProductResponse> action = invocation.getArgument(1);
            return action.get();
        });
    }

    private void simulateLockNotAcquired() {
        when(distributedLockService.executeWithLock(any(), any(), any())).thenAnswer(invocation -> {
            Supplier<ProductResponse> fallback = invocation.getArgument(2);
            return fallback.get();
        });
    }
}
