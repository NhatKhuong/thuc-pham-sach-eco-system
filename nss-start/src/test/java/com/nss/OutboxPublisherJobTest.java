package com.nss;

import com.nss.ddd.application.cronjob.OutboxPublisherJob;
import com.nss.ddd.domain.model.entity.OutboxEvent;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.infrastructure.config.KafkaTopicConfig;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm {@code OutboxPublisherJob} — repository và {@code KafkaTemplate} đều mock, không cần broker
 * Kafka thật (backlog 0032 Phase 4).
 * <p>
 * <b>Quy tắc bất biến số 4 của architecture/01-overview.md §6</b> là thứ hai ca đầu tiên khoá lại:
 * {@code markPublished} chỉ được gọi SAU KHI {@code send()} trả về ACK thành công; một dòng publish
 * thất bại phải giữ nguyên {@code PENDING} để chu kỳ sau retry — không được đánh dấu published, và
 * không được ném ra ngoài làm dừng cả batch.
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherJobTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisherJob genJob() {
        return new OutboxPublisherJob(outboxEventRepository, kafkaTemplate);
    }

    private SendResult<String, String> genSendResult(String topic) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "k", "v");
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0, 0, 0);
        return new SendResult<>(record, metadata);
    }

    private OutboxEvent genPendingEvent(Long id) {
        return new OutboxEvent()
                .setId(id)
                .setAggregateId("NSS-20260831-K7M2QX9P4T")
                .setEventType("OrderStatusChanged")
                .setPayload("{\"code\":\"NSS-20260831-K7M2QX9P4T\"}")
                .setStatus(OutboxEvent.STATUS_PENDING)
                .setCreatedAt(LocalDateTime.of(2026, 8, 31, 10, 30));
    }

    @Test
    @DisplayName("Publish thanh cong: gui dung topic/key/payload, ROI MOI markPublished")
    void publishSuccessMarksPublishedAfterAck() {
        OutboxEvent event = genPendingEvent(1L);
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC), eq("1"), eq(event.getPayload())))
                .thenReturn(CompletableFuture.completedFuture(genSendResult(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC)));

        genJob().publishPendingEvents();

        verify(kafkaTemplate).send(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC, "1", event.getPayload());
        verify(outboxEventRepository).markPublished(1L);
    }

    @Test
    @DisplayName("Kafka key la CHUOI HOA cua OutboxEvent.id, khong phai aggregateId")
    void kafkaKeyIsTheOutboxEventIdAsString() {
        OutboxEvent event = genPendingEvent(777L);
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(genSendResult(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC)));

        genJob().publishPendingEvents();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC), keyCaptor.capture(), any());
        assertEquals("777", keyCaptor.getValue());
    }

    @Test
    @DisplayName("Publish that bai (future loi): KHONG markPublished, KHONG nem ra ngoai — status van PENDING")
    void publishFailureLeavesEventPendingForRetry() {
        OutboxEvent failing = genPendingEvent(2L);
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(failing));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker khong ket noi duoc")));

        genJob().publishPendingEvents();

        verify(outboxEventRepository, never()).markPublished(any());
    }

    @Test
    @DisplayName("Mot dong loi KHONG chan cac dong con lai trong cung chu ky — publish tung dong doc lap")
    void oneFailingRowDoesNotBlockTheRestOfTheBatch() {
        OutboxEvent failing = genPendingEvent(3L);
        OutboxEvent healthy = genPendingEvent(4L);
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(failing, healthy));
        when(kafkaTemplate.send(eq(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC), eq("3"), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker khong ket noi duoc")));
        when(kafkaTemplate.send(eq(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC), eq("4"), any()))
                .thenReturn(CompletableFuture.completedFuture(genSendResult(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC)));

        genJob().publishPendingEvents();

        verify(outboxEventRepository, never()).markPublished(3L);
        verify(outboxEventRepository, times(1)).markPublished(4L);
    }

    @Test
    @DisplayName("Khong co dong PENDING nao: khong goi Kafka, khong goi markPublished")
    void emptyBatchDoesNothing() {
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of());

        genJob().publishPendingEvents();

        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(outboxEventRepository, never()).markPublished(any());
    }
}
