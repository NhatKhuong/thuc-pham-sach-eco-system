package com.nss.ddd.application.service.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.mapper.OrderMapper;
import com.nss.ddd.application.service.mail.MailAppService;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.repository.IdempotencyKeyRepository;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.infrastructure.config.KafkaTopicConfig;
import com.nss.ddd.infrastructure.mq.OrderStatusChangedMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

/**
 * Consumer của event {@code OrderStatusChanged} (architecture/01-overview.md §6, backlog 0032).
 * <p>
 * <b>{@code INSERT IGNORE idempotency_key} nằm BÊN TRONG transaction này</b> — quy tắc bất biến số 2
 * của §6: rollback thì dòng idempotency rollback theo, Kafka redeliver mới xử lý lại được. Đưa nó ra
 * ngoài transaction là âm thầm mất đơn (coding-conventions §8 mục 4).
 * <p>
 * <b>Không tự nuốt exception ở tầng này</b>, khác {@code MailAppServiceImpl}: một lỗi đọc DB ở đây
 * (khác exception mà mail tự nuốt ở biên SMTP) phải làm rollback transaction — bao gồm cả dòng
 * idempotency vừa chèn — để Kafka còn cơ hội redeliver. {@code @Transactional(rollbackFor =
 * Exception.class)} lo phần đó; method này không {@code catch} gì trừ việc parse {@code eventId}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusChangedConsumer {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    private final OrderDomainService orderDomainService;

    private final MailAppService mailAppService;

    private final ObjectMapper objectMapper;

    /**
     * {@code concurrency = "3"} khớp số partition của {@link KafkaTopicConfig#ORDER_STATUS_CHANGED_TOPIC}
     * (coding-conventions §11: {@code concurrency} phải {@code <=} số partition — vượt quá thì các
     * luồng thừa không bao giờ được gán partition nào, chạy mà không làm gì).
     *
     * @param payload chuỗi JSON của {@link OrderStatusChangedMessage}, nguyên vẹn từ {@code outbox_event.payload}
     * @param key Kafka record key — chuỗi hoá của {@code OutboxEvent.id}, dùng làm khoá idempotency
     */
    @KafkaListener(topics = KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC, concurrency = "3")
    @Transactional(rollbackFor = Exception.class)
    public void onOrderStatusChanged(@Payload String payload,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key) throws Exception {
        Long eventId = Long.valueOf(key);
        // 1. CONG IDEMPOTENCY — atomic, BEN TRONG transaction (quy tac bat bien so 2, §6).
        if (!idempotencyKeyRepository.tryInsert(eventId, LocalDateTime.now(ZoneOffset.UTC))) {
            log.info("[ORDER_STATUS_CHANGED] Duplicate skip eventId={}", eventId);
            return;
        }
        // 2. Giai ma payload SAU khi da qua cong idempotency — mot payload hong khong duoc lam
        //    mat quyen "duplicate thi bo qua" cua nhung lan redeliver sau.
        OrderStatusChangedMessage message = objectMapper.readValue(payload, OrderStatusChangedMessage.class);
        Order order = orderDomainService.findByCode(message.getCode());
        if (order == null) {
            // Ly thuyet khong xay ra — outbox va don ghi cung mot transaction — nhung khong doan,
            // khong nem NullPointerException giua duong xu ly. Idempotency key da chen: coi nhu
            // "da xu ly", khong retry vo han cho mot don khong con ton tai.
            log.warn("[ORDER_STATUS_CHANGED] Order khong ton tai, bo qua | eventId={} code={}",
                    eventId, message.getCode());
            return;
        }
        List<OrderItem> items = orderDomainService
                .findItemsGroupedByOrderId(List.of(order.getId()))
                .getOrDefault(order.getId(), Collections.emptyList());
        // 3. Goi mail — MailAppServiceImpl tu nuot moi exception cua chinh no (@Async + circuit
        //    breaker), nen mot loi SMTP o day KHONG lam rollback transaction nay.
        mailAppService.sendOrderStatusEmail(message.getShippingEmail(), order, items, message.getToStatus());
        log.info("[ORDER_STATUS_CHANGED] processed | eventId={} orderId={} code={} toStatus={}",
                eventId, order.getId(), order.getCode(), OrderMapper.toStatusLabel(message.getToStatus()));
    }
}
