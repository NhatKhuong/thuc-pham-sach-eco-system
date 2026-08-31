package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Cổng idempotency của consumer Kafka (architecture/01-overview.md §6, backlog 0032).
 * <p>
 * <b>Khoá tự nhiên là {@code id} của chính {@link OutboxEvent} đã publish nó</b> — không
 * {@code @GeneratedValue}, đúng khuôn "natural key" của coding-conventions §6. Consumer gửi kèm
 * {@code eventId} này qua Kafka record key, nên nó luôn có sẵn trước khi cần chèn dòng idempotency.
 * <p>
 * <b>Chỉ được ghi qua {@code INSERT IGNORE} BÊN TRONG transaction của consumer</b> — rollback thì
 * dòng này rollback theo, redelivery mới xử lý lại được (coding-conventions §8 mục 4). Không có
 * method {@code save} thường ở port này, cố ý — xem {@code IdempotencyKeyRepository#tryInsert}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "idempotency_key")
@Comment("Khoa idempotency cua consumer Kafka, tranh xu ly trung mot event")
public class IdempotencyKey {

    /** Tương ứng cột {@code event_id} — id của {@link OutboxEvent} đã sinh ra event này. */
    @Id
    @Column(nullable = false)
    @Comment("Id cua outbox_event tuong ung, khoa idempotent cho consumer")
    private Long eventId;

    /** Tương ứng cột {@code created_at} — thời điểm consumer xử lý thành công lần đầu, giờ UTC. */
    @Column(nullable = false)
    @Comment("Thoi diem consumer xu ly thanh cong lan dau, luu theo gio UTC")
    private LocalDateTime createdAt;
}
