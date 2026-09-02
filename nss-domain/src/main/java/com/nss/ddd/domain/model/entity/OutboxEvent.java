package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Dòng Outbox — nửa ghi của pattern Outbox + Kafka (architecture/01-overview.md §6, backlog 0032).
 * <p>
 * <b>Ghi cùng transaction với bản ghi nghiệp vụ, KHÔNG bao giờ gọi Kafka trực tiếp trong
 * request</b> — quy tắc bất biến số 1 của §6. {@link com.nss.ddd.domain.service.impl.OrderDomainServiceImpl}
 * không chạm bảng này; chỗ ghi duy nhất là {@code OrderAppServiceImpl}, ngay trong transaction đã mở
 * bởi {@code createOrder}/{@code changeOrderStatus}.
 * <p>
 * {@code payload} là chuỗi JSON đã dựng sẵn ở tầng application — entity này không biết gì về hình
 * dạng của {@code OrderStatusChangedMessage}, đúng ranh giới domain không phụ thuộc infrastructure.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outbox_event", indexes = {@Index(name = "idx_status_created", columnList = "status, created_at")})
@Comment("Outbox event cho pattern Outbox + Kafka")
public class OutboxEvent {

    /** Chưa publish lên Kafka — {@code OutboxPublisherJob} còn phải xử lý dòng này. */
    public static final int STATUS_PENDING = 0;

    /** Đã publish thành công và đã nhận ACK từ broker. */
    public static final int STATUS_PUBLISHED = 1;

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /**
     * Tương ứng cột {@code aggregate_id} — mã đơn ({@code Order.code}) cho event
     * {@code OrderStatusChanged}. Chuỗi trần, không khoá ngoại: outbox là hạ tầng dùng chung cho
     * nhiều aggregate, không riêng cho {@code Order}.
     */
    @Column(nullable = false, length = 64)
    @Comment("Id logic cua aggregate sinh ra event nay, vi du ma don")
    private String aggregateId;

    /** Tương ứng cột {@code event_type} — {@code "OrderStatusChanged"} cho backlog 0032. */
    @Column(nullable = false, length = 64)
    @Comment("Loai event, vi du OrderStatusChanged")
    private String eventType;

    /**
     * Tương ứng cột {@code partition_key} — Kafka record key TUỲ CHỌN (backlog 0039 Phase 3).
     * <p>
     * <b>{@code null} là mặc định và là ca của {@code OrderStatusChanged}</b>:
     * {@code OutboxPublisherJob} fallback về chuỗi hoá của {@link #id} khi cột này rỗng, giữ nguyên
     * 100% hành vi trước ticket 0039. Event {@code PurchaseRequested} đặt cột này bằng
     * {@code productId} (chuỗi hoá) để Kafka partition công bằng theo sản phẩm — xem
     * {@code KafkaTopicConfig} và javadoc {@code OutboxPublisherJob#publishRowByRow}.
     * <p>
     * <b>Vì record key giờ có thể KHÔNG còn là định danh event</b>, danh tính dùng cho idempotency
     * ở consumer chuyển sang truyền qua Kafka header {@code X-Event-Id} — xem
     * {@code KafkaTopicConfig#HEADER_EVENT_ID}. Đây là điểm khác biệt duy nhất giữa
     * {@code PurchaseRequestedConsumer} và {@code OrderStatusChangedConsumer} (cái sau vẫn đọc
     * {@code KafkaHeaders.RECEIVED_KEY} như cũ, vì key của nó luôn là {@link #id}).
     */
    @Column(length = 64)
    @Comment("Kafka record key tuy chon; null thi OutboxPublisherJob fallback ve chuoi hoa cua id")
    private String partitionKey;

    /** Tương ứng cột {@code payload} — chuỗi JSON đã dựng sẵn, gửi nguyên vẹn lên Kafka. */
    @Column(nullable = false, columnDefinition = "JSON")
    @Comment("Payload JSON gui len Kafka, dung nguyen ven khong bien doi lai")
    private String payload;

    /**
     * Tương ứng cột {@code status}.
     * <p>
     * {@code // 0=PENDING, 1=PUBLISHED}
     * <p>
     * <b>Chỉ chuyển 1 SAU KHI broker ACK</b> (quy tắc bất biến số 4 của §6) — xem
     * {@code OutboxPublisherJob.publishRowByRow}.
     */
    @Column(nullable = false)
    @Comment("Trang thai publish: 0=PENDING, 1=PUBLISHED")
    private Integer status;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, cùng mốc với bản ghi nghiệp vụ. */
    @Column(nullable = false)
    @Comment("Thoi diem ghi outbox, luu theo gio UTC")
    private LocalDateTime createdAt;
}
