package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.PurchaseRequest;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PORT của {@link PurchaseRequest} — domain khai báo, infrastructure implement (backlog 0039).
 * <p>
 * Ràng buộc kiến trúc giống {@code OutboxEventRepository}: file này không import bất cứ thứ gì
 * thuộc {@code org.springframework.data.*}.
 */
public interface PurchaseRequestRepository {

    /**
     * @param idempotencyKey chuỗi client cung cấp qua header {@code Idempotency-Key}
     * @return request đã tồn tại với đúng key này, hoặc rỗng khi chưa có
     */
    Optional<PurchaseRequest> findByIdempotencyKey(String idempotencyKey);

    /**
     * @param requestId khoá chính, dạng {@code PR-<16 hex>}
     * @return request tương ứng, hoặc rỗng khi không có
     */
    Optional<PurchaseRequest> findByRequestId(String requestId);

    /**
     * {@code INSERT IGNORE} trên khoá duy nhất {@code idempotency_key} — cổng chống client retry
     * HTTP atomic, cùng khuôn với {@code IdempotencyKeyRepository#tryInsert}.
     *
     * @param request bản ghi cần chèn, {@code status} đã là {@link PurchaseRequest#STATUS_PENDING}
     * @return {@code true} — chèn được (key mới); {@code false} — đã tồn tại (client retry đúng
     *         idempotency key, phía gọi phải đọc lại bằng {@link #findByIdempotencyKey})
     */
    boolean tryInsert(PurchaseRequest request);

    /**
     * Đánh dấu thành công — gọi bởi {@code PurchaseRequestedConsumer} sau khi
     * {@code OrderAppService#createOrderInNewTransaction} trả về một đơn.
     *
     * @param requestId khoá chính
     * @param orderCode mã đơn đã tạo
     * @param updatedAt thời điểm cập nhật, giờ UTC
     */
    void markSuccess(String requestId, String orderCode, LocalDateTime updatedAt);

    /**
     * Đánh dấu thất bại nghiệp vụ — gọi bởi {@code PurchaseRequestedConsumer} khi
     * {@code OrderAppService#createOrderInNewTransaction} trả về một mã lỗi.
     *
     * @param requestId       khoá chính
     * @param failureCode     mã lỗi nghiệp vụ UPPER_SNAKE
     * @param failureMessage  thông điệp tiếng Việt cho người dùng cuối
     * @param updatedAt       thời điểm cập nhật, giờ UTC
     */
    void markFailed(String requestId, String failureCode, String failureMessage, LocalDateTime updatedAt);

    /**
     * {@code created_at} của bản ghi {@code PENDING} cũ nhất — nguồn của gauge
     * {@code purchase_request_pending_age_seconds} (backlog 0039 Phase 7).
     *
     * @return thời điểm submit của request PENDING cũ nhất, hoặc rỗng khi không có request nào đang
     *         PENDING
     */
    Optional<LocalDateTime> findOldestPendingCreatedAt();
}
