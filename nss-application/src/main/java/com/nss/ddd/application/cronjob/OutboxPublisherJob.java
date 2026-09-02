package com.nss.ddd.application.cronjob;

import com.nss.ddd.domain.model.entity.OutboxEvent;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.infrastructure.config.KafkaTopicConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publisher của pattern Outbox + Kafka (architecture/01-overview.md §6, backlog 0032).
 * <p>
 * <b>Chỉ {@code UPDATE status=1} SAU KHI broker ACK</b> — quy tắc bất biến số 4 của §6. Một dòng
 * publish thất bại (broker chết, timeout) giữ nguyên {@code status=0} để chu kỳ sau retry; job
 * <b>không</b> tự xoá hay đánh dấu lỗi cho một dòng thất bại — nó chỉ chưa xong.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherJob {

    /** Số dòng tối đa xử lý trong một chu kỳ — khớp con số của blueprint §6. */
    private static final int BATCH_SIZE = 500;

    /** Thời gian tối đa chờ ACK của MỖI dòng trước khi coi là thất bại và để lại cho chu kỳ sau. */
    private static final long ACK_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outboxEventRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingBatch(BATCH_SIZE);
        for (OutboxEvent event : pending) {
            publishRowByRow(event);
        }
    }

    /**
     * Publish đúng MỘT dòng, đồng bộ chờ ACK trước khi đánh dấu {@code PUBLISHED}.
     * <p>
     * <b>Đánh đổi: từng dòng một, đồng bộ — thay vì gửi cả lô rồi chờ tất cả ACK song song.</b>
     * <ul>
     *   <li><b>+</b> Một dòng lỗi (payload hỏng, broker từ chối riêng bản ghi này) không kéo theo
     *       những dòng còn lại của cùng chu kỳ — mỗi dòng có kết quả publish độc lập, đúng ngữ nghĩa
     *       "publish từng event" mà Outbox pattern hứa hẹn.</li>
     *   <li><b>+</b> Đơn giản để đọc và để test: không cần gom {@code CompletableFuture} rồi xử lý
     *       lỗi từng phần của một {@code allOf()}.</li>
     *   <li><b>−</b> Chậm hơn publish theo lô ở thông lượng cao — mỗi dòng trả giá đúng
     *       {@code ACK_TIMEOUT_SECONDS} khi broker chậm, và {@code BATCH_SIZE} dòng lỗi liên tiếp có
     *       thể chiếm hết chu kỳ 1 giây. Chấp nhận được ở quy mô hiện tại; nếu thông lượng đơn hàng
     *       tăng mạnh, đổi sang gửi lô + {@code CompletableFuture.allOf} là hướng tối ưu tiếp theo.</li>
     * </ul>
     *
     * <p>
     * <b>Tổng quát hoá theo {@code event_type} (backlog 0039 Phase 3)</b> — topic tra qua
     * {@link KafkaTopicConfig#resolveTopic(String)}, record key ưu tiên
     * {@link OutboxEvent#getPartitionKey()} (fallback về chuỗi hoá {@link OutboxEvent#getId()} khi
     * {@code null}, đúng 100% hành vi trước ticket 0039 cho {@code OrderStatusChanged}). Header
     * {@link KafkaTopicConfig#HEADER_EVENT_ID} luôn được gắn thêm — {@code OrderStatusChangedConsumer}
     * không đọc nó (vẫn dùng record key như cũ) nên gắn thêm không đổi hành vi của consumer đó;
     * {@code PurchaseRequestedConsumer} thì BẮT BUỘC đọc header này vì record key của nó mang
     * {@code productId}, không còn là định danh event.
     *
     * @param event dòng outbox đang {@code PENDING}
     */
    private void publishRowByRow(OutboxEvent event) {
        try {
            String topic = KafkaTopicConfig.resolveTopic(event.getEventType());
            String key = event.getPartitionKey() != null
                    ? event.getPartitionKey()
                    : String.valueOf(event.getId());
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, event.getPayload());
            record.headers().add(KafkaTopicConfig.HEADER_EVENT_ID,
                    String.valueOf(event.getId()).getBytes(StandardCharsets.UTF_8));

            SendResult<String, String> result = kafkaTemplate.send(record)
                    .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outboxEventRepository.markPublished(event.getId());
            log.info("publishPendingEvents: published | eventId={} aggregateId={} partition={} offset={}",
                    event.getId(), event.getAggregateId(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        } catch (TimeoutException e) {
            log.warn("publishPendingEvents: ack timeout, status van la PENDING de retry | eventId={}",
                    event.getId());
        } catch (Exception e) {
            // KHONG update status — status=0 giu nguyen, chu ky sau (fixedDelay=1000ms) se thu lai.
            log.error("publishPendingEvents: publish that bai, status van la PENDING de retry | eventId={}",
                    event.getId(), e);
        }
    }
}
