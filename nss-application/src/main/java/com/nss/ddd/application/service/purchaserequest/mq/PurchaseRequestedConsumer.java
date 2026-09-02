package com.nss.ddd.application.service.purchaserequest.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.mapper.PurchaseRequestMapper;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.service.order.OrderAppService;
import com.nss.ddd.domain.repository.IdempotencyKeyRepository;
import com.nss.ddd.domain.repository.PurchaseRequestRepository;
import com.nss.ddd.infrastructure.config.KafkaTopicConfig;
import com.nss.ddd.infrastructure.mq.PurchaseRequestedMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Consumer của event {@code PurchaseRequested} (backlog 0039 Phase 5, architecture/01-overview.md
 * §6).
 * <p>
 * <b>Điểm khác biệt DUY NHẤT so với {@code OrderStatusChangedConsumer}: eventId đọc từ HEADER, không
 * từ record key.</b> Record key của topic {@code purchase.requested} mang {@code productId} để
 * partition công bằng theo sản phẩm (§Contract, backlog 0039 Phase 3) — nó không còn là định danh
 * event, nên danh tính dùng cho cổng idempotency chuyển sang Kafka header
 * {@link KafkaTopicConfig#HEADER_EVENT_ID}, do {@code OutboxPublisherJob} gắn thêm cho MỌI event bất
 * kể type.
 * <p>
 * <b>{@code INSERT IGNORE idempotency_key} vẫn nằm BÊN TRONG transaction này</b> — quy tắc bất biến
 * số 2 của §6, không đổi so với {@code OrderStatusChangedConsumer}: rollback thì key cũng rollback,
 * Kafka redeliver mới xử lý lại được.
 * <p>
 * <b>Tạo đơn qua {@link OrderAppService#createOrderInNewTransaction(CreateOrderCommand)}</b>
 * (backlog 0039 Phase 1) — KHÔNG {@link OrderAppService#createOrder(CreateOrderCommand)} thường:
 * một business failure (hết hàng…) chỉ rollback transaction con REQUIRES_NEW của việc tạo đơn, không
 * cascade ra transaction ngoài của chính consumer này — nếu cascade, dòng {@code idempotency_key}
 * vừa chèn và dòng {@code purchase_request.FAILED} cần ghi lại đều bị xoá theo.
 * <p>
 * <b>Kết quả tạo đơn map bằng GIÁ TRỊ TRẢ VỀ (Pattern A), không phải exception</b>
 * (coding-conventions §11): {@code result.getOrder() != null} là {@code SUCCESS}, ngược lại là
 * {@code FAILED} kèm {@code code}/{@code message} của {@link OrderMutationResponse}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseRequestedConsumer {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    private final OrderAppService orderAppService;

    private final PurchaseRequestRepository purchaseRequestRepository;

    private final ObjectMapper objectMapper;

    /**
     * {@code concurrency = "3"} khớp số partition của
     * {@link KafkaTopicConfig#PURCHASE_REQUESTED_TOPIC} (coding-conventions §11).
     *
     * @param payload   chuỗi JSON của {@link PurchaseRequestedMessage}, nguyên vẹn từ
     *                  {@code outbox_event.payload}
     * @param eventIdHeader chuỗi hoá của {@code OutboxEvent.id}, đọc từ header
     *                      {@link KafkaTopicConfig#HEADER_EVENT_ID} — KHÔNG từ record key (khác
     *                      {@code OrderStatusChangedConsumer}, xem javadoc cấp class)
     */
    @KafkaListener(topics = KafkaTopicConfig.PURCHASE_REQUESTED_TOPIC,
            groupId = "nss-purchase-request", concurrency = "3")
    @Transactional(rollbackFor = Exception.class)
    public void onPurchaseRequested(@Payload String payload,
                                     @Header(KafkaTopicConfig.HEADER_EVENT_ID) String eventIdHeader) throws Exception {
        Long eventId = Long.valueOf(eventIdHeader);
        // 1. CONG IDEMPOTENCY — atomic, BEN TRONG transaction (quy tac bat bien so 2, §6).
        if (!idempotencyKeyRepository.tryInsert(eventId, LocalDateTime.now(ZoneOffset.UTC))) {
            log.info("[PURCHASE_REQUESTED] Duplicate skip eventId={}", eventId);
            return;
        }
        // 2. Giai ma payload SAU khi da qua cong idempotency.
        PurchaseRequestedMessage message = objectMapper.readValue(payload, PurchaseRequestedMessage.class);
        CreateOrderCommand command = PurchaseRequestMapper.toCommand(message);

        // 3. Tao don trong TRANSACTION RIENG (REQUIRES_NEW, backlog 0039 Phase 1) — xem javadoc
        //    cap class ve ly do khong dung createOrder thuong.
        OrderMutationResponse result = orderAppService.createOrderInNewTransaction(command);

        // 4. Map ket qua (gia tri tra ve, Pattern A) sang purchase_request.
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        if (result.getOrder() != null) {
            purchaseRequestRepository.markSuccess(message.getRequestId(), result.getOrder().getCode(), nowUtc);
            log.info("[PURCHASE_REQUESTED] processed SUCCESS | eventId={} requestId={} orderCode={}",
                    eventId, message.getRequestId(), result.getOrder().getCode());
        } else {
            purchaseRequestRepository.markFailed(message.getRequestId(), result.getCode(), result.getMessage(), nowUtc);
            log.info("[PURCHASE_REQUESTED] processed FAILED | eventId={} requestId={} code={}",
                    eventId, message.getRequestId(), result.getCode());
        }
    }
}
