package com.nss.ddd.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

/**
 * Khai báo topic Kafka của hệ thống (architecture/01-overview.md §6, backlog 0032/0039).
 * <p>
 * Hằng {@code *_TOPIC} khai ở đây, dùng chung cho publisher lẫn consumer — cùng lý do
 * {@code MailAppServiceImpl.CIRCUIT_BREAKER_NAME} là hằng: một chuỗi topic gõ tay ở hai nơi là hai
 * chỗ có thể lệch nhau, và triệu chứng của lệch là "publish một nơi, không ai nghe ở nơi kia".
 * <p>
 * <b>3 partition — quyết định kỹ thuật ngoài spec của ticket, cần PM/Owner xác nhận nếu muốn đổi.</b>
 * Đủ để chạy song song vài consumer instance ở quy mô hiện tại của hệ thống mà không phải đổi lại
 * ngay; {@code @KafkaListener(concurrency = "3")} của {@code OrderStatusChangedConsumer} VÀ
 * {@code PurchaseRequestedConsumer} khớp đúng con số này (coding-conventions §11: {@code concurrency}
 * phải {@code <=} số partition). Replication factor 1 vì dev stack chỉ có một broker (KRaft
 * single-node).
 * <p>
 * <b>{@link #EVENT_TYPE_TOPICS} là bảng ánh xạ {@code event_type} → topic (backlog 0039 Phase 3)</b>
 * — chỗ duy nhất {@code OutboxPublisherJob} tra để biết publish một dòng vào topic nào, mà không
 * phải biết gì về payload/shape của từng loại event. Hằng {@code EVENT_TYPE_*} khai chung ở đây thay
 * vì để mỗi tầng application tự đặt chuỗi trần: {@code OrderAppServiceImpl} và
 * {@code PurchaseRequestAppServiceImpl} đều tham chiếu lại đúng các hằng này khi ghi
 * {@code outbox_event.event_type} — một chuỗi gõ tay ở hai nơi (nơi ghi và nơi map ra topic) là hai
 * chỗ có thể lệch nhau đúng kiểu lỗi mà javadoc lớp này đã cảnh báo ở trên.
 */
@Configuration
public class KafkaTopicConfig {

    /** Topic của event {@code OrderStatusChanged} — tên do ticket 0032 đề ra. */
    public static final String ORDER_STATUS_CHANGED_TOPIC = "order.status-changed";

    /** Topic của event {@code PurchaseRequested} — tên do ticket 0039 đề ra (Luồng B, mua hàng). */
    public static final String PURCHASE_REQUESTED_TOPIC = "purchase.requested";

    /** Giá trị {@code outbox_event.event_type} cho pipeline {@code OrderStatusChanged} (backlog 0032). */
    public static final String EVENT_TYPE_ORDER_STATUS_CHANGED = "OrderStatusChanged";

    /** Giá trị {@code outbox_event.event_type} cho pipeline {@code PurchaseRequested} (backlog 0039). */
    public static final String EVENT_TYPE_PURCHASE_REQUESTED = "PurchaseRequested";

    /**
     * Header Kafka mang {@code OutboxEvent.id} (backlog 0039 Phase 3) — danh tính dùng cho
     * idempotency ở {@code PurchaseRequestedConsumer}, tách khỏi record key vì record key của topic
     * này mang {@code productId} để partition công bằng, không còn là định danh event.
     * {@code OrderStatusChangedConsumer} không dùng header này — nó vẫn đọc
     * {@code KafkaHeaders.RECEIVED_KEY} như trước ticket 0039, vì record key của topic đó luôn là
     * {@code OutboxEvent.id}.
     */
    public static final String HEADER_EVENT_ID = "X-Event-Id";

    private static final int PARTITION_COUNT = 3;

    private static final short REPLICATION_FACTOR = 1;

    /**
     * Ánh xạ {@code event_type} → topic — xem javadoc cấp class.
     * <p>
     * Một {@code event_type} không có mặt ở đây là lỗi cấu hình (quên đăng ký), nên
     * {@code OutboxPublisherJob} fail nhanh với {@code IllegalStateException} thay vì âm thầm bỏ
     * qua dòng đó mãi mãi — xem {@link #resolveTopic(String)}.
     */
    private static final Map<String, String> EVENT_TYPE_TOPICS = Map.of(
            EVENT_TYPE_ORDER_STATUS_CHANGED, ORDER_STATUS_CHANGED_TOPIC,
            EVENT_TYPE_PURCHASE_REQUESTED, PURCHASE_REQUESTED_TOPIC);

    /**
     * @param eventType {@code outbox_event.event_type} của dòng đang publish
     * @return tên topic tương ứng
     * @throws IllegalStateException khi {@code eventType} không có trong {@link #EVENT_TYPE_TOPICS}
     *                                — một event_type mới ra đời mà quên đăng ký topic ở đây
     */
    public static String resolveTopic(String eventType) {
        String topic = EVENT_TYPE_TOPICS.get(eventType);
        if (topic == null) {
            throw new IllegalStateException(
                    "KafkaTopicConfig: khong biet topic cho outbox_event.event_type=" + eventType);
        }
        return topic;
    }

    @Bean
    public NewTopic orderStatusChangedTopic() {
        return TopicBuilder.name(ORDER_STATUS_CHANGED_TOPIC)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic purchaseRequestedTopic() {
        return TopicBuilder.name(PURCHASE_REQUESTED_TOPIC)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
