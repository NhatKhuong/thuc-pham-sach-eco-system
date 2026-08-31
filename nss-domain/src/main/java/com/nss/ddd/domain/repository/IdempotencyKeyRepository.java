package com.nss.ddd.domain.repository;

import java.time.LocalDateTime;

/**
 * PORT của {@link com.nss.ddd.domain.model.entity.IdempotencyKey} — domain khai báo, infrastructure
 * implement.
 */
public interface IdempotencyKeyRepository {

    /**
     * {@code INSERT IGNORE} — cổng idempotency atomic.
     * <p>
     * <b>Phải được gọi BÊN TRONG transaction của consumer</b>, trước mọi write nghiệp vụ khác
     * (coding-conventions §8 mục 4) — rollback thì dòng này rollback theo, và Kafka redeliver mới xử
     * lý lại được.
     *
     * @param eventId id của {@code OutboxEvent} đã publish event này
     * @param processedAt thời điểm xử lý, giờ UTC
     * @return {@code true} — key mới, tiếp tục xử lý; {@code false} — duplicate (Kafka retry /
     *         rebalance), skip
     */
    boolean tryInsert(Long eventId, LocalDateTime processedAt);
}
