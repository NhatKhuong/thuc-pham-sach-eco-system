package com.nss.ddd.application.service.product.cache.impl;

import com.nss.ddd.application.mapper.ProductMapper;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.application.service.product.cache.ProductCacheService;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.service.ProductDomainService;
import com.nss.ddd.infrastructure.cache.redis.ProductCacheRedisAdapter;
import com.nss.ddd.infrastructure.distributed.redisson.DistributedLockService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Hiện thực cache 3 tầng cho {@code GET /products/{slug}} (backlog 0035 Phase 1,
 * architecture/01-overview.md §4).
 * <p>
 * <b>Guava là {@code final Cache} khai trực tiếp trên field, không phải bean Spring</b> — giống mọi
 * cache process-local khác, nó phải sống và chết cùng vòng đời instance, không được chia sẻ qua DI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheServiceImpl implements ProductCacheService {

    /**
     * TTL Guava L1 — <b>60 giây</b>, KHÔNG phải 5 phút mặc định của architecture/01-overview.md §4.
     * <p>
     * <b>Độ lệch CÓ CHỦ Ý — Quyết định Owner #3 (backlog 0035).</b> Owner chấp nhận staleness bị
     * chặn ở một instance thay vì xây pub/sub đồng bộ tức thời giữa các instance khi admin sửa sản
     * phẩm, nhưng siết ngưỡng xuống 1 phút thay vì 5 phút: một trang chi tiết sản phẩm flash sale
     * hiển thị sai giá/tồn kho quá lâu sau khi admin sửa là rủi ro nghiệp vụ lớn hơn chi phí thêm
     * vài lần chạm Redis mỗi phút cho mỗi sản phẩm hot. Xem note tương ứng ở
     * {@code architecture/01-overview.md} §4.
     */
    private static final long GUAVA_TTL_SECONDS = 60;

    /**
     * TTL Redis L2 — 10 phút. Đây là <b>lưới an toàn</b> cho lần evict chủ động bị bỏ sót, không
     * phải cơ chế chính (architecture §4) — evict tường minh ở {@code updateProduct}/
     * {@code deleteProduct} mới là cơ chế chính giữ Redis đồng bộ với MySQL.
     */
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);

    /** Cache L1 process-local — key là {@code slug}. */
    private final Cache<String, ProductResponse> localCache = CacheBuilder.newBuilder()
            .expireAfterWrite(GUAVA_TTL_SECONDS, TimeUnit.SECONDS)
            .build();

    private final ProductDomainService productDomainService;

    private final ProductCacheRedisAdapter productCacheRedisAdapter;

    private final DistributedLockService distributedLockService;

    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     * <p>
     * Bốn bước, đúng thứ tự architecture §4: Guava → Redis → khoá quanh DB (double-check Redis
     * trong khoá) → DB không khoá khi không lấy được khoá.
     */
    @Override
    public ProductResponse getProductBySlug(String slug) {
        ProductResponse cached = localCache.getIfPresent(slug);
        if (cached != null) {
            return cached;
        }
        String key = genProductKey(slug);
        ProductResponse fromRedis = getFromRedis(key);
        if (fromRedis != null) {
            localCache.put(slug, fromRedis);
            return fromRedis;
        }
        // Ca hai tang mem deu miss — khoa quanh lan doc DB de chong cache stampede (Phase 3).
        return distributedLockService.executeWithLock(genLoadLockKey(slug),
                () -> {
                    // Double-check BEN TRONG khoa: instance khac co the vua nap xong trong luc
                    // minh cho lay khoa (architecture §4).
                    ProductResponse doubleChecked = getFromRedis(key);
                    if (doubleChecked != null) {
                        localCache.put(slug, doubleChecked);
                        return doubleChecked;
                    }
                    return loadAndWarm(slug, key);
                },
                // Khong lay duoc khoa trong 1s -> doc DB khong khoa, an toan cuoi (architecture §4).
                () -> loadAndWarm(slug, key));
    }

    @Override
    public void evict(String slug) {
        if (slug == null) {
            return;
        }
        localCache.invalidate(slug);
        productCacheRedisAdapter.evict(genProductKey(slug));
        log.info("evict: da xoa cache san pham | slug={}", slug);
    }

    // ========== HELPERS ==========

    /**
     * Đọc sản phẩm thẳng từ DB (logic dời nguyên trạng từ
     * {@code ProductAppServiceImpl.findProductBySlug} cũ) rồi nạp lại cả hai tầng cache.
     * <p>
     * <b>Không cache kết quả rỗng</b> — một slug chưa tồn tại (hoặc gõ sai) không được để lại một
     * dấu vết "miss vĩnh viễn 60 giây" trong Guava; sản phẩm vừa được tạo phải xuất hiện ngay ở lần
     * gọi kế tiếp.
     *
     * @param slug slug cần đọc
     * @param key khoá Redis tương ứng, đã sinh sẵn ở {@link #getProductBySlug(String)}
     * @return sản phẩm, hoặc {@code null} khi không tồn tại / đã bị xoá mềm
     */
    private ProductResponse loadAndWarm(String slug, String key) {
        Product product = productDomainService.findBySlug(slug);
        if (product == null) {
            log.warn("loadAndWarm: khong tim thay san pham | slug={}", slug);
            return null;
        }
        ProductResponse response = ProductMapper.toResponse(product, productDomainService.findImages(product.getId()));
        String json = serialize(response);
        if (json != null) {
            productCacheRedisAdapter.set(key, json, REDIS_TTL);
        }
        localCache.put(slug, response);
        return response;
    }

    /**
     * @param key khoá Redis
     * @return sản phẩm đã deserialize, hoặc {@code null} khi miss / Redis lỗi / JSON hỏng
     */
    private ProductResponse getFromRedis(String key) {
        String json = productCacheRedisAdapter.get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductResponse.class);
        } catch (JsonProcessingException e) {
            // JSON hong (vi du sau khi doi hinh dang ProductResponse ma Redis con giu ban cu) — coi
            // nhu miss, KHONG lam gay luong doc (coding-conventions §11).
            log.warn("getFromRedis: JSON hong, coi nhu cache miss | key={}", key, e);
            return null;
        }
    }

    /**
     * @param response payload cần serialize
     * @return JSON, hoặc {@code null} khi serialize thất bại — lỗi này log rồi bỏ qua ghi Redis,
     *         không được làm gãy luồng đọc
     */
    private String serialize(ProductResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.warn("serialize: khong serialize duoc ProductResponse, bo qua ghi Redis | slug={}",
                    response.getSlug(), e);
            return null;
        }
    }

    /**
     * Key dữ liệu tầng Redis — {@code PRODUCT:ITEM:{slug}}, đúng namespace Data object của
     * architecture §4. Theo slug, không theo id: slug có thể đổi khi admin sửa, key-theo-id đòi
     * thêm một index phụ {@code SLUG→id} phải đồng bộ hai key — xem "Plan" của backlog 0035.
     *
     * @param slug slug của sản phẩm
     * @return khoá Redis
     */
    private String genProductKey(String slug) {
        return "PRODUCT:ITEM:" + slug;
    }

    /**
     * Key khoá phân tán quanh lần nạp lại cache — {@code LOCK:PRODUCT_CACHE_LOAD:{slug}}, đúng
     * namespace Distributed lock của architecture §4.
     *
     * @param slug slug của sản phẩm
     * @return khoá Redis của lock
     */
    private String genLoadLockKey(String slug) {
        return "LOCK:PRODUCT_CACHE_LOAD:" + slug;
    }
}
