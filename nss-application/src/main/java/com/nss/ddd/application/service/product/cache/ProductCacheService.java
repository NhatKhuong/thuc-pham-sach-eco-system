package com.nss.ddd.application.service.product.cache;

import com.nss.ddd.application.model.response.ProductResponse;

/**
 * Cache 3 tầng (Guava → Redis → MySQL) cho {@code GET /products/{slug}} (backlog 0035 Phase 1,
 * architecture/01-overview.md §4).
 * <p>
 * <b>Chỉ cache trang chi tiết — không cache {@code GET /products} (listing).</b> Không gian key của
 * listing bùng nổ (9 chiều filter × 5 kiểu sort) và hit rate thấp; xem "Non-goal" của backlog 0035.
 * <p>
 * <b>Kiểu Java thuần trên chữ ký</b> — không có {@code RedisTemplate}/{@code RLock} nào lộ ra đây,
 * đúng tinh thần "application không biết Redis tồn tại" (coding-conventions §13 mở rộng); mọi chi
 * tiết Redis nằm trong {@code ProductCacheServiceImpl} (dùng {@code ProductCacheRedisAdapter} +
 * {@code DistributedLockService} của {@code nss-infrastructure} qua chiều phụ thuộc Maven sẵn có
 * application → infrastructure) và trong chính adapter đó.
 */
public interface ProductCacheService {

    /**
     * Tra sản phẩm theo slug qua cache 3 tầng.
     * <p>
     * Thuật toán: Guava hit → trả; miss → Redis hit → nạp lại Guava, trả; miss cả hai → khoá phân
     * tán quanh lần đọc DB (chống stampede), double-check Redis bên trong khoá trước khi thật sự
     * đọc DB; không lấy được khoá thì đọc DB không khoá làm an toàn cuối.
     *
     * @param slug slug của sản phẩm
     * @return sản phẩm, hoặc {@code null} khi không tồn tại / đã bị xoá mềm — kết quả rỗng
     *         <b>không</b> được cache, để lần gọi kế tiếp thấy ngay khi sản phẩm ra đời
     */
    ProductResponse getProductBySlug(String slug);

    /**
     * Xoá cache của một slug ở <b>cả hai tầng</b> (Guava của instance đang xử lý request này, và
     * Redis dùng chung mọi instance) — gọi ngay sau khi admin ghi thành công sản phẩm
     * ({@code updateProduct}, {@code deleteProduct}).
     * <p>
     * <b>Guava là process-local</b>: evict ở đây chỉ xoá bản sao của <i>instance đang xử lý request
     * ghi này</i>. Các instance khác vẫn phục vụ bản Guava cũ tới khi TTL 60 giây của chúng hết hạn
     * — đây là staleness đã được Owner chấp nhận có giới hạn (Quyết định #3), không phải một khiếm
     * khuyết của phương thức này.
     *
     * @param slug slug cần xoá cache; {@code null} là no-op
     */
    void evict(String slug);
}
