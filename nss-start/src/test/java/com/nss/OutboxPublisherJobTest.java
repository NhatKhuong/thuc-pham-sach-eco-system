package com.nss;

import com.nss.ddd.application.cronjob.OutboxPublisherJob;
import com.nss.ddd.domain.model.entity.OutboxEvent;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.infrastructure.config.KafkaTopicConfig;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm {@code OutboxPublisherJob} — repository và {@code KafkaTemplate} đều mock, không cần broker
 * Kafka thật (backlog 0032 Phase 4, tổng quát hoá theo {@code event_type} ở backlog 0039 Phase 3).
 * <p>
 * <b>Quy tắc bất biến số 4 của architecture/01-overview.md §6</b> là thứ hai ca đầu tiên khoá lại:
 * {@code markPublished} chỉ được gọi SAU KHI {@code send()} trả về ACK thành công; một dòng publish
 * thất bại phải giữ nguyên {@code PENDING} để chu kỳ sau retry — không được đánh dấu published, và
 * không được ném ra ngoài làm dừng cả batch.
 * <p>
 * <b>Kể từ backlog 0039, job gửi qua overload {@code send(ProducerRecord)}</b> (không còn
 * {@code send(topic, key, payload)}) để có chỗ gắn thêm header {@code X-Event-Id} — mọi stub/verify
 * ở đây đều nhắm vào overload đó, đọc lại topic/key/header từ chính {@link ProducerRecord} đã gửi.
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
                .setEventType(KafkaTopicConfig.EVENT_TYPE_ORDER_STATUS_CHANGED)
                .setPayload("{\"code\":\"NSS-20260831-K7M2QX9P4T\"}")
                .setStatus(OutboxEvent.STATUS_PENDING)
                .setCreatedAt(LocalDateTime.of(2026, 8, 31, 10, 30));
    }

    @Test
    @DisplayName("Publish thanh cong: gui dung topic/key/payload, ROI MOI markPublished")
    void publishSuccessMarksPublishedAfterAck() {
        OutboxEvent event = genPendingEvent(1L);
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(genSendResult(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC)));

        genJob().publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertEquals(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC, sent.topic());
        assertEquals("1", sent.key());
        assertEquals(event.getPayload(), sent.value());
        verify(outboxEventRepository).markPublished(1L);
    }

    @Test
    @DisplayName("Khong co partition_key: Kafka key la CHUOI HOA cua OutboxEvent.id (fallback, khop hanh vi truoc 0039)")
    void kafkaKeyFallsBackToOutboxEventIdWhenPartitionKeyIsNull() {
        OutboxEvent event = genPendingEvent(777L);
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(genSendResult(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC)));

        genJob().publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertEquals("777", captor.getValue().key());
    }

    @Test
    @DisplayName("Co partition_key: Kafka key la CHINH partition_key, khong phai id (backlog 0039 Phase 3)")
    void kafkaKeyUsesPartitionKeyWhenPresent() {
        OutboxEvent event = genPendingEvent(888L)
                .setEventType(KafkaTopicConfig.EVENT_TYPE_PURCHASE_REQUESTED)
                .setPartitionKey("42");
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(genSendResult(KafkaTopicConfig.PURCHASE_REQUESTED_TOPIC)));

        genJob().publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertEquals(KafkaTopicConfig.PURCHASE_REQUESTED_TOPIC, sent.topic());
        assertEquals("42", sent.key());
    }

    @Test
    @DisplayName("Header X-Event-Id luon mang chuoi hoa cua OutboxEvent.id, doc lap voi record key")
    void headerAlwaysCarriesOutboxEventId() {
        OutboxEvent event = genPendingEvent(999L)
                .setEventType(KafkaTopicConfig.EVENT_TYPE_PURCHASE_REQUESTED)
                .setPartitionKey("42");
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(genSendResult(KafkaTopicConfig.PURCHASE_REQUESTED_TOPIC)));

        genJob().publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        Header header = captor.getValue().headers().lastHeader(KafkaTopicConfig.HEADER_EVENT_ID);
        assertTrue(header != null, "thieu header " + KafkaTopicConfig.HEADER_EVENT_ID);
        assertEquals("999", new String(header.value(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("event_type khong dang ky topic nao: khong markPublished, khong nem ra ngoai lam dung batch")
    void unknownEventTypeIsTreatedAsFailureAndLeavesRowPending() {
        OutboxEvent event = genPendingEvent(5L).setEventType("KhongTonTai");
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(event));

        genJob().publishPendingEvents();

        verify(outboxEventRepository, never()).markPublished(any());
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("Publish that bai (future loi): KHONG markPublished, KHONG nem ra ngoai — status van PENDING")
    void publishFailureLeavesEventPendingForRetry() {
        OutboxEvent failing = genPendingEvent(2L);
        when(outboxEventRepository.findPendingBatch(anyInt())).thenReturn(List.of(failing));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
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
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker khong ket noi duoc")))
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

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(outboxEventRepository, never()).markPublished(any());
    }
}
