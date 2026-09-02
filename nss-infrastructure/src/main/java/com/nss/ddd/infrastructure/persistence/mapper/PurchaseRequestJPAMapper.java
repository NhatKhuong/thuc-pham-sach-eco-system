package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.PurchaseRequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data interface của {@code purchase_request} — hạ tầng thuần, không mang quy tắc nghiệp vụ
 * (backlog 0039).
 */
public interface PurchaseRequestJPAMapper extends JpaRepository<PurchaseRequest, String> {

    /**
     * @param idempotencyKey chuỗi client cung cấp qua header {@code Idempotency-Key}
     * @return request khớp key, hoặc rỗng
     */
    Optional<PurchaseRequest> findByIdempotencyKey(String idempotencyKey);

    /**
     * Request {@code PENDING} cũ nhất — dùng {@code idx_status_created} (backlog 0039 Phase 7).
     *
     * @param status {@link PurchaseRequest#STATUS_PENDING}
     * @return request PENDING cũ nhất, hoặc rỗng khi không có
     */
    Optional<PurchaseRequest> findFirstByStatusOrderByCreatedAtAsc(Integer status);

    /**
     * {@code INSERT IGNORE} — native query bắt buộc cho ca này (coding-conventions §12), cùng
     * khuôn với {@code IdempotencyKeyJPAMapper#tryInsert}: JPQL {@code save()} thường sẽ ném
     * {@code DataIntegrityViolationException} trên khoá {@code idempotency_key} trùng thay vì trả
     * về một con số rows-affected đọc được.
     *
     * @return {@code 1} khi chèn được (key mới), {@code 0} khi đã tồn tại (duplicate)
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO purchase_request "
            + "(request_id, idempotency_key, user_id, status, created_at, updated_at) "
            + "VALUES (:requestId, :idempotencyKey, :userId, :status, :createdAt, :updatedAt)",
            nativeQuery = true)
    int tryInsert(@Param("requestId") String requestId,
                  @Param("idempotencyKey") String idempotencyKey,
                  @Param("userId") Long userId,
                  @Param("status") int status,
                  @Param("createdAt") LocalDateTime createdAt,
                  @Param("updatedAt") LocalDateTime updatedAt);
}
