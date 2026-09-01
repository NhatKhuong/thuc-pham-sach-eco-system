package com.nss.ddd.infrastructure.cache.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Adapter Redis <b>tổng quát</b> cho tầng L2 của cache 3 tầng (backlog 0035 Phase 1,
 * architecture/01-overview.md §4).
 * <p>
 * <b>Chỉ biết chuỗi và TTL, không biết "sản phẩm" là gì.</b> Việc sinh key
 * ({@code PRODUCT:ITEM:{slug}}), việc quyết định TTL, và việc serialize/deserialize JSON đều là việc
 * của {@code ProductCacheServiceImpl} (application) — file này chỉ là một lớp mỏng bọc
 * {@link StringRedisTemplate}, đúng ranh giới "Redis chỉ được chạm từ {@code *-application} (qua
 * {@code *CacheService}) và {@code *-infrastructure}" (coding-conventions §13).
 * <p>
 * <b>Không bao giờ ném exception ra ngoài.</b> Mọi lỗi hạ tầng (Redis chết, timeout) đều bị nuốt tại
 * đây, {@code log.warn}, rồi trả về giá trị coi như "cache miss" / "bỏ qua ghi" — đúng luật "thất bại
 * của cache không được làm gãy luồng nghiệp vụ" (coding-conventions §11). Đặt việc bắt lỗi ở ranh
 * giới thấp nhất (ngay tại điểm gọi Redis) để {@code ProductCacheServiceImpl} không phải tự bọc
 * try/catch quanh từng lời gọi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCacheRedisAdapter {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * @param key khoá Redis, đã sinh sẵn ở phía gọi
     * @return giá trị JSON đang lưu, hoặc {@code null} khi không có / Redis không tới được
     */
    public String get(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("get: Redis khong toi duoc, coi nhu cache miss | key={}", key, e);
            return null;
        }
    }

    /**
     * Ghi kèm TTL — {@code set} trần không TTL bị cấm (coding-conventions §13/§17).
     *
     * @param key khoá Redis
     * @param value giá trị JSON đã serialize sẵn
     * @param ttl thời gian sống, bắt buộc
     */
    public void set(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (RuntimeException e) {
            log.warn("set: Redis khong toi duoc, bo qua ghi cache | key={}", key, e);
        }
    }

    /**
     * @param key khoá cần xoá — dùng cho evict chủ động khi admin sửa/xoá sản phẩm
     */
    public void evict(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("evict: Redis khong toi duoc, bo qua evict | key={}", key, e);
        }
    }
}
