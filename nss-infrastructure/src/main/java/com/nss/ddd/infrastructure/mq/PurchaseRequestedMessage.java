package com.nss.ddd.infrastructure.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Payload JSON của event {@code PurchaseRequested} trên Kafka (backlog 0039 §Contract, Phase 4).
 * <p>
 * <b>Bản chụp đầy đủ của {@code CreateOrderCommand}</b> cộng {@link #requestId} — đủ để
 * {@code PurchaseRequestedConsumer} dựng lại nguyên vẹn lệnh tạo đơn mà không cần đọc thêm gì từ
 * request gốc (đã kết thúc từ lâu khi consumer chạy).
 * <p>
 * <b>Danh tính của event (khoá idempotency) KHÔNG nằm trong payload này</b>, cùng lý do đã ghi ở
 * {@code OrderStatusChangedMessage}: nó đi qua Kafka header
 * {@code KafkaTopicConfig#HEADER_EVENT_ID}, không phải record key (record key của topic này mang
 * {@code productId} để partition công bằng — xem {@code KafkaTopicConfig}).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestedMessage {

    /** Khoá của {@code purchase_request} — dùng để {@code markSuccess}/{@code markFailed} sau khi xử lý. */
    private String requestId;

    /** Chủ request; {@code null} là khách vãng lai — khớp {@code CreateOrderCommand#getUserId()}. */
    private Long userId;

    /** Các dòng hàng khách muốn mua. */
    private List<PurchaseRequestedItemMessage> items;

    /** Thông tin giao hàng, bản chụp tại thời điểm submit. */
    private PurchaseRequestedShippingMessage shipping;

    /** Phương thức thanh toán ở dạng chuỗi của dây, chưa dịch sang {@code int}. */
    private String paymentMethod;

    /** Mã giảm giá khách áp; {@code null} hoặc rỗng nghĩa là không áp mã. */
    private String couponCode;
}
