package com.nss.ddd.infrastructure.distributed.redisson.impl;

import com.nss.ddd.infrastructure.distributed.redisson.DistributedLockService;

import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Hiện thực {@link DistributedLockService} bằng Redisson (backlog 0035 Phase 3,
 * architecture/01-overview.md §4, coding-conventions §13).
 * <p>
 * <b>{@code tryLock(1, 5, TimeUnit.SECONDS)}</b> — chờ tối đa 1 giây để lấy khoá (đủ ngắn để không
 * biến "trang bị reload dồn dập" thành "request treo lâu"), giữ khoá tối đa 5 giây (lease — đủ cho
 * một lượt đọc DB rồi ghi lại cache, tự nhả nếu tiến trình giữ khoá chết bất thường mà không kịp
 * {@code unlock()}).
 * <p>
 * <b>Không bao giờ {@code lock()} trần</b> (coding-conventions §13/§17) — chỉ {@code tryLock} có
 * timeout, và {@code finally} luôn kiểm {@code isLocked() && isHeldByCurrentThread()} trước khi
 * {@code unlock()}: một luồng khác có thể đã hết lease và bị Redisson tự nhả khoá hộ, gọi
 * {@code unlock()} vô điều kiện lúc đó sẽ ném {@code IllegalMonitorStateException} trên khoá của
 * người khác.
 * <p>
 * <b>Hai điểm có thể hỏng đều rơi về {@code fallback}, không rơi về exception:</b> chờ khoá bị ngắt
 * ({@code InterruptedException}) và Redis không tới được (Redisson ném {@code RuntimeException} —
 * ví dụ {@code RedisConnectionException} — ngay ở {@code getLock}/{@code tryLock} vì
 * {@code RedissonClient} là bean {@code @Lazy}, xem javadoc {@code RedissonConfig}). Cả hai đều
 * {@code log.warn} rồi chạy {@code fallback} — cache/lock lỗi không được làm gãy luồng đọc sản phẩm
 * hay luồng đặt hàng (coding-conventions §11).
 */
@Slf4j
@Component
public class RedissonDistributedLockServiceImpl implements DistributedLockService {

    /** Thời gian tối đa chờ lấy khoá — architecture §4. */
    private static final long WAIT_SECONDS = 1;

    /** Thời gian giữ khoá tối đa (lease) trước khi Redisson tự nhả — architecture §4. */
    private static final long LEASE_SECONDS = 5;

    private final RedissonClient redissonClient;

    /**
     * <b>Constructor tay, không {@code @RequiredArgsConstructor}, vì {@code @Lazy} phải nằm trên
     * chính điểm tiêm.</b> {@code @Lazy} khai ở {@code @Bean redissonClient()} của
     * {@code RedissonConfig} là chưa đủ: Spring container vẫn phân giải (và do đó tạo thật)
     * dependency bắt buộc của constructor injection ngay khi dựng bean này, trừ khi chính tham số
     * constructor cũng được đánh dấu {@code @Lazy} — chỉ khi đó Spring mới tiêm một proxy và hoãn
     * {@code Redisson.create()} tới lần gọi method thật đầu tiên (trong khối try/catch bên dưới).
     * Thiếu dòng này, một Redis chết sẽ làm vỡ context của MỌI {@code @SpringBootTest} nạp đủ
     * component, kể cả những test không đụng gì tới cache/lock — đúng lỗi đã đo được khi thiếu nó.
     */
    public RedissonDistributedLockServiceImpl(@Lazy RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> action, Supplier<T> fallback) {
        RLock lock = null;
        boolean locked = false;
        try {
            lock = redissonClient.getLock(lockKey);
            locked = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("executeWithLock: bi ngat khi cho lock | lockKey={}", lockKey, e);
        } catch (RuntimeException e) {
            // Redis khong toi duoc (RedissonClient @Lazy, lan goi dau tien moi thuc su ket noi) —
            // day la cho "stampede protection khong giu duoc hoan toan" ma architecture §4/Phase 3
            // canh bao, KHONG phai loi lam gay luong doc.
            log.warn("executeWithLock: loi ha tang khi lay lock, roi ve fallback khong khoa | lockKey={}",
                    lockKey, e);
        }
        try {
            if (locked) {
                return action.get();
            }
            log.warn("executeWithLock: khong lay duoc lock trong {}s, doc khong khoa (an toan cuoi) | lockKey={}",
                    WAIT_SECONDS, lockKey);
            return fallback.get();
        } finally {
            if (locked && lock != null) {
                try {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (RuntimeException e) {
                    log.warn("executeWithLock: unlock that bai | lockKey={}", lockKey, e);
                }
            }
        }
    }
}
