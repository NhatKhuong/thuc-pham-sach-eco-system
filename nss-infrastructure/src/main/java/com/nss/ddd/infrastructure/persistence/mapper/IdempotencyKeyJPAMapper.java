package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.IdempotencyKey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Spring Data interface của {@code idempotency_key} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 */
public interface IdempotencyKeyJPAMapper extends JpaRepository<IdempotencyKey, Long> {

    /**
     * {@code INSERT IGNORE} — native query bắt buộc cho ca này (coding-conventions §12): JPQL
     * {@code save()} thường sẽ ném {@code DataIntegrityViolationException} trên khoá trùng thay vì
     * trả về một con số rows-affected đọc được.
     * <p>
     * <b>KHÔNG tự khai {@code @Transactional} ở đây</b> — method này phải chạy TRONG transaction đã
     * mở bởi consumer (coding-conventions §8 mục 4); tự mở một transaction riêng ở tầng mapper sẽ
     * tách rời {@code INSERT} idempotency khỏi phần rollback của consumer.
     *
     * @param eventId id của {@code OutboxEvent} đã publish event này
     * @param createdAt thời điểm xử lý, giờ UTC
     * @return {@code 1} khi chèn được (key mới), {@code 0} khi đã tồn tại (duplicate)
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO idempotency_key (event_id, created_at) VALUES (:eventId, :createdAt)",
            nativeQuery = true)
    int tryInsert(@Param("eventId") Long eventId, @Param("createdAt") LocalDateTime createdAt);
}
