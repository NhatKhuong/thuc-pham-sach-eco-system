package com.nss.ddd.infrastructure.distributed.redisson;

import java.util.function.Supplier;

/**
 * Cổng khoá phân tán chống <b>cache stampede</b> (backlog 0035 Phase 3, architecture/01-overview.md
 * §4).
 * <p>
 * <b>Vai trò duy nhất: khi cache nguội cho một khoá nóng, chỉ đúng một request được đi tiếp vào
 * MySQL nạp lại cache — các request đồng thời khác hoặc chờ rồi dùng lại cache vừa nạp, hoặc (nếu
 * không chờ được) tự đọc DB không khoá.</b> Interface này KHÔNG chỉ dành riêng cho sản phẩm — bất kỳ
 * luồng nào cần chặn nhiều request cùng nạp lại một khoá nóng đều dùng lại được.
 * <p>
 * <b>Không bao giờ ném exception ra ngoài</b> — một Redis chết hay một lần chờ khoá hết hạn đều phải
 * kết thúc bằng {@code fallback}, không phải bằng lỗi (coding-conventions §11: "Thất bại của cache
 * làm gãy luồng nghiệp vụ" là cấm tuyệt đối). Xem javadoc {@code RedissonDistributedLockServiceImpl}.
 */
public interface DistributedLockService {

    /**
     * Chạy {@code action} bên trong một khoá phân tán ứng với {@code lockKey}; nếu không lấy được
     * khoá trong thời gian chờ (hoặc Redis không tới được), chạy {@code fallback} thay thế —
     * <b>không khoá</b>.
     * <p>
     * <b>Bên trong {@code action}, phía gọi phải tự double-check điều kiện đã khiến nó cần khoá</b>
     * (ví dụ đọc lại Redis xem có instance khác vừa nạp xong chưa) — interface này chỉ đảm bảo tại
     * một thời điểm có tối đa một luồng đang chạy {@code action} cho cùng {@code lockKey}, nó không
     * biết gì về ý nghĩa nghiệp vụ của điều kiện đó.
     *
     * @param lockKey khoá Redis của lock, sinh qua {@code genXxxKey(...)} ở phía gọi — không inline
     * @param action chạy khi lấy được khoá
     * @param fallback chạy khi không lấy được khoá trong thời gian chờ, hoặc khi thao tác khoá gặp
     *                 lỗi hạ tầng (Redis không tới được) — <b>không được ném exception</b>
     * @return kết quả của {@code action} hoặc {@code fallback}, tuỳ nhánh nào chạy
     */
    <T> T executeWithLock(String lockKey, Supplier<T> action, Supplier<T> fallback);
}
