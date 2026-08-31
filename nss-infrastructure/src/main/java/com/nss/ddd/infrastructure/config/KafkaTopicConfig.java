package com.nss.ddd.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Khai báo topic Kafka của hệ thống (architecture/01-overview.md §6, backlog 0032).
 * <p>
 * Hằng {@code *_TOPIC} khai ở đây, dùng chung cho publisher lẫn consumer — cùng lý do
 * {@code MailAppServiceImpl.CIRCUIT_BREAKER_NAME} là hằng: một chuỗi topic gõ tay ở hai nơi là hai
 * chỗ có thể lệch nhau, và triệu chứng của lệch là "publish một nơi, không ai nghe ở nơi kia".
 * <p>
 * <b>3 partition — quyết định kỹ thuật ngoài spec của ticket, cần PM/Owner xác nhận nếu muốn đổi.</b>
 * Đủ để chạy song song vài consumer instance ở quy mô hiện tại của hệ thống mà không phải đổi lại
 * ngay; {@code @KafkaListener(concurrency = "3")} của {@code OrderStatusChangedConsumer} khớp đúng
 * con số này (coding-conventions §11: {@code concurrency} phải {@code <=} số partition).
 * Replication factor 1 vì dev stack chỉ có một broker (KRaft single-node).
 */
@Configuration
public class KafkaTopicConfig {

    /** Topic của event {@code OrderStatusChanged} — tên do ticket 0032 đề ra. */
    public static final String ORDER_STATUS_CHANGED_TOPIC = "order.status-changed";

    private static final int PARTITION_COUNT = 3;

    private static final short REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic orderStatusChangedTopic() {
        return TopicBuilder.name(ORDER_STATUS_CHANGED_TOPIC)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
