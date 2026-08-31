package com.nss.ddd.infrastructure.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload JSON của event {@code OrderStatusChanged} trên Kafka (backlog 0032 §Contract).
 * <p>
 * <b>Sáu trường này là toàn bộ hợp đồng của payload</b> — nội bộ {@code api}, không phải public API
 * (ticket §Contract), nên không cần đồng bộ với {@code app}/{@code android}/{@code ios}.
 * <p>
 * <b>Danh tính của event (khoá idempotency) KHÔNG nằm trong payload này.</b> {@code OutboxPublisherJob}
 * dùng {@code OutboxEvent.id} làm Kafka record key thay vì nhét thêm một trường vào JSON — payload
 * được serialize <i>trước</i> khi dòng outbox có id (chưa {@code INSERT} thì chưa có
 * {@code AUTO_INCREMENT}), nên nhét id vào chính payload sẽ cần một lần {@code UPDATE} thừa ngay
 * trong transaction ghi đơn. {@code OrderStatusChangedConsumer} đọc {@code eventId} từ
 * {@code KafkaHeaders.RECEIVED_KEY}, không từ payload.
 * <p>
 * <b>{@code changedAt} là chuỗi ISO-8601 UTC</b> ({@code OrderMapper#toIsoUtc} cùng khuôn), không
 * phải {@code LocalDateTime}: tránh phải thêm {@code jackson-datatype-jsr310} chỉ cho một trường.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusChangedMessage {

    /** {@code Order.id} — khoá chính, dùng để lần vết trong log. */
    private Long orderId;

    /** {@code Order.code} — dùng để tra lại đơn đầy đủ (item, giá) ở consumer. */
    private String code;

    /** Trạng thái trước khi chuyển; {@code null} nghĩa là đơn vừa được tạo. */
    private Integer fromStatus;

    /**
     * Trạng thái sau khi chuyển — <b>đây là trạng thái consumer phải hiển thị trong email</b>, không
     * phải trạng thái hiện tại đọc lại từ {@code Order.status}. Đơn có thể đã chuyển tiếp trạng thái
     * khác trước khi consumer xử lý xong event cũ hơn; dùng giá trị snapshot ở đây tránh email của
     * một transition cũ hiển thị nhầm trạng thái mới nhất.
     */
    private Integer toStatus;

    /** {@code Order.shipping.email} tại thời điểm ghi event — recipient của email thông báo. */
    private String shippingEmail;

    /** Thời điểm chuyển trạng thái, chuỗi ISO-8601 UTC (vd. {@code 2026-08-31T10:30:00Z}). */
    private String changedAt;
}
