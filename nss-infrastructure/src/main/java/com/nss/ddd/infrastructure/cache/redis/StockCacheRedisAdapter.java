package com.nss.ddd.infrastructure.cache.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Adapter Redis cho cổng atomic Lua của tồn kho (backlog 0035 Phase 2, architecture/01-overview.md
 * §5, coding-conventions §13).
 * <p>
 * <b>Contract trả về của {@link #deductStock}: {@code -1} miss, {@code 0} không đủ, {@code 1} đã
 * trừ</b> — đúng nguyên văn architecture §5. <b>Khác pseudocode minh hoạ ở đúng một chỗ, và đó là
 * SỬA LỖI chứ không phải đổi contract:</b> nhánh {@code SET} thành công của tài liệu dùng {@code SET}
 * trần, thứ sẽ <b>xoá mất TTL</b> của counter — vi phạm chính luật "mọi giá trị cache phải có TTL"
 * (§13/§17). Ở đây thêm {@code KEEPTTL} vào lệnh {@code SET} đó.
 * <p>
 * <b>Warm-on-miss dùng {@code SET key value NX EX ttl}</b> ({@link #warmIfAbsent}), không phải
 * {@code SET} trần — hai request đua nhau warm cùng lúc (một request A gọi warm rồi retry-deduct
 * thành công, một request B tới sau vẫn tưởng đang warm) sẽ không đè mất giá trị vừa bị trừ bởi lần
 * retry của A nếu dùng {@code NX}: request B thấy key đã tồn tại (do A vừa tạo) nên tự bỏ qua, giữ
 * nguyên giá trị A vừa trừ.
 * <p>
 * <b>{@link #increaseStock} (compensation/hoàn kho) cố ý CHỈ tăng khi key đã tồn tại, KHÔNG bao giờ
 * tự tạo key mới.</b> {@code INCRBY} trên một key chưa tồn tại sẽ tạo nó <b>không TTL</b> — vi phạm
 * §13/§17 lần thứ hai nếu dùng thẳng {@code INCRBY}. Nếu counter đang cold (chưa warm) thì không có
 * gì để "hoàn" vào cache — MySQL Tầng 2 vẫn là nguồn sự thật, lần warm kế tiếp sẽ tự đọc đúng giá trị
 * DB hiện tại.
 * <p>
 * Không bao giờ ném exception ra ngoài — mọi lỗi hạ tầng đều {@code log.warn} rồi trả về giá trị an
 * toàn (coding-conventions §11): {@code deductStock} trả {@code -1} (coi như miss, tầng gọi sẽ vẫn đi
 * tiếp xuống Tầng 2 MySQL), {@code increaseStock}/{@code warmIfAbsent} là no-op.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockCacheRedisAdapter {

    /**
     * Cổng atomic trừ kho — architecture §5, có thêm {@code KEEPTTL} so với pseudocode gốc (xem
     * javadoc cấp class).
     */
    private static final String LUA_DEDUCT_STOCK =
            "local stock = redis.call('GET', KEYS[1]); "
                    + "if stock == false then return -1 end; "
                    + "stock = tonumber(stock); "
                    + "if (stock >= tonumber(ARGV[1])) then "
                    + "    redis.call('SET', KEYS[1], stock - tonumber(ARGV[1]), 'KEEPTTL'); "
                    + "    return 1; "
                    + "end; "
                    + "return 0;";

    private static final DefaultRedisScript<Long> SCRIPT_DEDUCT_STOCK =
            new DefaultRedisScript<>(LUA_DEDUCT_STOCK, Long.class);

    /** Hoàn kho — chỉ tăng khi key đã tồn tại, xem javadoc cấp class. */
    private static final String LUA_INCREASE_STOCK =
            "if redis.call('EXISTS', KEYS[1]) == 1 then "
                    + "    redis.call('INCRBY', KEYS[1], ARGV[1]); "
                    + "end; "
                    + "return 1;";

    private static final DefaultRedisScript<Long> SCRIPT_INCREASE_STOCK =
            new DefaultRedisScript<>(LUA_INCREASE_STOCK, Long.class);

    /**
     * TTL warm — 24 giờ. An toàn vì Tầng 2 MySQL luôn chạy đồng bộ ngay sau Tầng 1 trong Luồng A, nên
     * counter tự làm mới đúng giá trị mỗi khi hết hạn — không có rủi ro double-count hay stale-restore
     * (architecture §5, Plan Phase 2 của backlog 0035).
     */
    private static final Duration WARM_TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * @param productId khoá chính sản phẩm
     * @param quantity số lượng cần trừ, phải dương
     * @return {@code 1} đã trừ, {@code 0} không đủ, {@code -1} miss (cold cache) <b>hoặc</b> Redis
     *         không tới được — hai ca sau cố ý gộp làm một, vì cả hai đều nghĩa là "Tầng 1 không
     *         chặn được gì, cứ đi tiếp xuống Tầng 2"
     */
    public int deductStock(Long productId, int quantity) {
        try {
            Long result = stringRedisTemplate.execute(SCRIPT_DEDUCT_STOCK,
                    List.of(genStockKey(productId)), String.valueOf(quantity));
            return result == null ? -1 : result.intValue();
        } catch (RuntimeException e) {
            log.warn("deductStock: Redis khong toi duoc, coi nhu mien Tang 1 | productId={} quantity={}",
                    productId, quantity, e);
            return -1;
        }
    }

    /**
     * Hoàn kho (SAGA compensation, architecture §5) — cộng lại {@code quantity} nếu counter đang
     * warm; no-op nếu counter đang cold.
     *
     * @param productId khoá chính sản phẩm
     * @param quantity số lượng cần hoàn, phải dương
     */
    public void increaseStock(Long productId, int quantity) {
        try {
            stringRedisTemplate.execute(SCRIPT_INCREASE_STOCK, List.of(genStockKey(productId)),
                    String.valueOf(quantity));
        } catch (RuntimeException e) {
            log.warn("increaseStock: Redis khong toi duoc, bo qua hoan kho cache | productId={} quantity={}",
                    productId, quantity, e);
        }
    }

    /**
     * Warm-on-miss — {@code SET key value NX EX ttl}, xem javadoc cấp class về lý do bắt buộc
     * {@code NX}.
     *
     * @param productId khoá chính sản phẩm
     * @param stock giá trị tồn kho hiện tại, đọc từ nguồn đã có sẵn trong request (không query thêm)
     */
    public void warmIfAbsent(Long productId, long stock) {
        try {
            stringRedisTemplate.opsForValue().setIfAbsent(genStockKey(productId), String.valueOf(stock), WARM_TTL);
        } catch (RuntimeException e) {
            log.warn("warmIfAbsent: Redis khong toi duoc, bo qua warm | productId={}", productId, e);
        }
    }

    /**
     * Namespace Counter chuẩn {@code <DOMAIN>:{id}:<FIELD>} (architecture §4).
     *
     * @param productId khoá chính sản phẩm
     * @return khoá Redis
     */
    private String genStockKey(Long productId) {
        return "PRODUCT:" + productId + ":STOCK";
    }
}
