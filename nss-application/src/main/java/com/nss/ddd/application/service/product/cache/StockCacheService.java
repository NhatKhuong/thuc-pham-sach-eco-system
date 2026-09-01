package com.nss.ddd.application.service.product.cache;

/**
 * Cổng atomic Redis (Lua) cho tồn kho — Tầng 1 của bất biến "không oversell" (backlog 0035 Phase 2,
 * architecture/01-overview.md §5).
 * <p>
 * <b>Redis là cổng atomic. MySQL là lưới an toàn. Cache đã trừ thì phải hoàn.</b> Ba câu này của §5
 * là toàn bộ lý do interface này tồn tại: nó KHÔNG thay thế
 * {@code OrderDomainService#deductStock} (Tầng 2, conditional UPDATE MySQL) — nó đứng TRƯỚC Tầng 2
 * để chặn phần lớn tải trước khi chạm MySQL, và Tầng 2 vẫn luôn được gọi để chốt chặn cuối
 * (xem {@code OrderAppServiceImpl.createOrder}).
 * <p>
 * <b>Kiểu Java thuần trên chữ ký</b> — không {@code RedisScript}/{@code StringRedisTemplate} nào lộ
 * ra đây; mọi chi tiết Lua nằm ở {@code StockCacheRedisAdapter} (infrastructure).
 */
public interface StockCacheService {

    /** Đã trừ thành công. */
    int DEDUCTED = 1;

    /** Không đủ tồn kho theo Redis. */
    int INSUFFICIENT = 0;

    /**
     * Cold cache (key chưa có, kể cả sau khi đã warm+retry) <b>hoặc</b> Redis không tới được — hai
     * ca cố ý dùng chung một mã, vì phía gọi xử lý giống nhau: cứ để Tầng 2 MySQL quyết định, không
     * coi đây là {@code OUT_OF_STOCK}.
     */
    int MISS = -1;

    /**
     * Trừ tồn kho qua cổng Lua atomic — <b>tự warm-on-miss và retry đúng một lần</b>, phía gọi không
     * phải tự lặp.
     * <p>
     * Thuật toán (backlog 0035 Phase 2, architecture §5): gọi Lua deduct; nếu {@link #MISS} (key
     * chưa có), warm bằng {@code knownStock} — giá trị <b>đã có sẵn trong request</b> của phía gọi
     * (ví dụ {@code Product} vừa đọc để dựng dòng hàng), <b>không query DB thêm</b> — rồi gọi lại Lua
     * deduct <b>đúng một lần nữa</b>. Kết quả của lần thứ hai là kết quả cuối cùng, dù nó vẫn là
     * {@link #MISS} (ví dụ Redis vừa chết giữa hai lần gọi) — không có lần thứ ba.
     *
     * @param productId khoá chính sản phẩm
     * @param quantity số lượng cần trừ, phải dương
     * @param knownStock giá trị tồn kho hiện tại dùng để warm khi miss — phía gọi tự đọc từ dữ liệu
     *                   đã có sẵn, method này không tự đi query
     * @return {@link #DEDUCTED}, {@link #INSUFFICIENT}, hoặc {@link #MISS}
     */
    int deductStock(Long productId, int quantity, long knownStock);

    /**
     * Hoàn kho (SAGA compensation) — gọi ngay khi một dòng đã {@link #DEDUCTED} nhưng transaction
     * đặt hàng thất bại ở một bước sau đó, hoặc khi đơn chuyển sang {@code CANCELLED}.
     *
     * @param productId khoá chính sản phẩm
     * @param quantity số lượng cần hoàn, phải dương — đúng số lượng đã trừ trước đó
     */
    void increaseStock(Long productId, int quantity);
}
