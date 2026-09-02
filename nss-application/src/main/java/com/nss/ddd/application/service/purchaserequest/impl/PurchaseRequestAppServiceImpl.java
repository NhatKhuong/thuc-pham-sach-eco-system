package com.nss.ddd.application.service.purchaserequest.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.mapper.PurchaseRequestMapper;
import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.response.PurchaseRequestResponse;
import com.nss.ddd.application.service.purchaserequest.PurchaseRequestAppService;
import com.nss.ddd.domain.model.entity.OutboxEvent;
import com.nss.ddd.domain.model.entity.PurchaseRequest;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.domain.repository.PurchaseRequestRepository;
import com.nss.ddd.infrastructure.config.KafkaTopicConfig;
import com.nss.ddd.infrastructure.mq.PurchaseRequestedMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Hiện thực use case của luồng mua hàng bất đồng bộ (backlog 0039 Phase 4).
 * <p>
 * <b>Rẻ có chủ đích.</b> Method ghi duy nhất ở đây chỉ chèn {@code purchase_request} +
 * {@code outbox_event} trong một transaction ngắn — không Redis, không MySQL stock, không coupon.
 * Toàn bộ phần "đắt" (§Contract 9 của {@code createOrder}) chạy sau, bất đồng bộ, ở
 * {@code PurchaseRequestedConsumer}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRequestAppServiceImpl implements PurchaseRequestAppService {

    /** Tiền tố cố định của {@code requestId} — phần còn lại là ngẫu nhiên hex. */
    private static final String REQUEST_ID_PREFIX = "PR-";

    /** Bảng chữ cái phần ngẫu nhiên — hex thuần, đúng như §Contract mô tả {@code PR-<16 hex>}. */
    private static final String REQUEST_ID_ALPHABET = "0123456789abcdef";

    /** Độ dài phần ngẫu nhiên — 16 ký tự hex = 64 bit, đủ để tránh đụng độ ngẫu nhiên trong thực tế. */
    private static final int REQUEST_ID_RANDOM_LENGTH = 16;

    /**
     * Nguồn ngẫu nhiên của {@code requestId} — {@code SecureRandom}, cùng lý do đã ghi ở
     * {@code OrderDomainServiceImpl#ORDER_CODE_RANDOM}: không đoán được xuôi/ngược, và
     * {@code requestId} là token duy nhất khoá {@code GET /orders/requests/{requestId}} công khai
     * (§Contract — "token là chính requestId, không đoán được").
     */
    private static final SecureRandom REQUEST_ID_RANDOM = new SecureRandom();

    private final PurchaseRequestRepository purchaseRequestRepository;

    private final OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequestResponse submitAsync(CreateOrderCommand command, String idempotencyKey) {
        // 1. Fast path: key da tung thay -> tra lai DUNG requestId/status hien tai, KHONG tao outbox
        //    moi (§Contract). Day la doc-truoc-khi-ghi, chi la toi uu — INSERT IGNORE o buoc 3 moi
        //    la luoi an toan that su chong race giua hai request dong thoi cung key.
        Optional<PurchaseRequest> existing = purchaseRequestRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("submitAsync: idempotent replay | idempotencyKey={} requestId={}",
                    idempotencyKey, existing.get().getRequestId());
            return PurchaseRequestMapper.toResponse(existing.get());
        }

        // 2. Sinh requestId MOI va dung draft PENDING.
        String requestId = genRequestId();
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        PurchaseRequest draft = new PurchaseRequest()
                .setRequestId(requestId)
                .setIdempotencyKey(idempotencyKey)
                .setUser(genUserReference(command.getUserId()))
                .setStatus(PurchaseRequest.STATUS_PENDING)
                .setCreatedAt(nowUtc)
                .setUpdatedAt(nowUtc);

        // 3. INSERT IGNORE tren uk_idempotency_key — cong an toan THAT chong hai request dong thoi
        //    cung Idempotency-Key (buoc 1 chi la toi uu doc truoc). Thua cuoc dua -> doc lai ban ghi
        //    cua nguoi thang, KHONG tao outbox (dung nguoi thang moi duoc tao).
        if (!purchaseRequestRepository.tryInsert(draft)) {
            PurchaseRequest winner = purchaseRequestRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "submitAsync: tryInsert that bai nhung khong doc lai duoc ban ghi da ton tai"
                                    + " | idempotencyKey=" + idempotencyKey));
            log.info("submitAsync: thua race dieu kien, dung ban ghi cua nguoi thang | idempotencyKey={} requestId={}",
                    idempotencyKey, winner.getRequestId());
            return PurchaseRequestMapper.toResponse(winner);
        }

        // 4. Outbox event PurchaseRequested, CUNG transaction voi buoc 3 — khong bao gio goi Kafka
        //    truc tiep trong request (bat bien so 1, architecture §6).
        genOutboxPurchaseRequestedEvent(draft, command);

        log.info("submitAsync: success | requestId={} userId={} itemCount={}",
                requestId, command.getUserId(), genItemCount(command));
        return PurchaseRequestMapper.toResponse(draft);
    }

    @Override
    public PurchaseRequestResponse findByRequestId(String requestId) {
        PurchaseRequestResponse response = purchaseRequestRepository.findByRequestId(requestId)
                .map(PurchaseRequestMapper::toResponse)
                .orElse(null);
        if (response == null) {
            log.warn("findByRequestId: not found | requestId={}", requestId);
        } else {
            log.info("findByRequestId: success | requestId={} status={}", requestId, response.getStatus());
        }
        return response;
    }

    // ========== HELPERS ==========

    /**
     * Ghi outbox event {@code PurchaseRequested} — cùng khuôn với
     * {@code OrderAppServiceImpl#genOutboxOrderStatusChangedEvent}.
     * <p>
     * <b>Partition key = {@code productId} của item ĐẦU TIÊN</b> (backlog 0039 Phase 4, giới hạn đã
     * ghi ở ticket: giỏ nhiều sản phẩm vẫn hoạt động đúng, chỉ công bằng FIFO theo sản phẩm đầu).
     * Giỏ rỗng (hợp lệ ở tầng DTO — {@code @NotNull} không {@code @NotEmpty}) thì không có
     * {@code productId} nào để partition theo — để {@code null}, {@code OutboxPublisherJob} tự
     * fallback về {@code OutboxEvent.id}.
     */
    private void genOutboxPurchaseRequestedEvent(PurchaseRequest draft, CreateOrderCommand command) {
        PurchaseRequestedMessage message = PurchaseRequestMapper.toMessage(command, draft.getRequestId());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Khong the serialize PurchaseRequestedMessage cho request " + draft.getRequestId(), e);
        }
        outboxEventRepository.save(new OutboxEvent()
                .setAggregateId(draft.getRequestId())
                .setEventType(KafkaTopicConfig.EVENT_TYPE_PURCHASE_REQUESTED)
                .setPartitionKey(genPartitionKey(command.getItems()))
                .setPayload(payload)
                .setStatus(OutboxEvent.STATUS_PENDING)
                .setCreatedAt(draft.getCreatedAt()));
    }

    /**
     * @param items giỏ hàng của lệnh
     * @return {@code productId} của dòng đầu tiên (chuỗi hoá), hoặc {@code null} khi giỏ rỗng/dòng
     *         đầu thiếu {@code productId}
     */
    private String genPartitionKey(List<CartItemCommand> items) {
        if (items == null || items.isEmpty() || items.get(0).getProductId() == null) {
            return null;
        }
        return String.valueOf(items.get(0).getProductId());
    }

    /**
     * Dựng tham chiếu {@code User} NHẸ — chỉ mang {@code id}, KHÔNG query DB.
     * <p>
     * <b>Cố ý không load {@code User} thật</b> — đúng tinh thần "rẻ có chủ đích" của method này
     * (xem javadoc cấp class): submit chỉ cần ghi đúng khoá ngoại {@code user_id}, không cần đọc
     * hồ sơ người dùng. Hibernate chấp nhận một instance {@code User} chỉ có {@code id} (không
     * {@code null}) làm giá trị {@code @ManyToOne} vì chiến lược {@code IDENTITY} coi một id khác
     * {@code null} là "đã tồn tại" — không ném {@code TransientObjectException} — cùng khuôn tối ưu
     * đã dùng ở nơi khác của hệ thống khi chỉ cần ghi khoá ngoại mà không cần toàn bộ bản ghi cha.
     *
     * @param userId chủ request; {@code null} là khách vãng lai
     * @return tham chiếu {@code User} mang đúng id, hoặc {@code null} khi khách vãng lai
     */
    private User genUserReference(Long userId) {
        return userId == null ? null : new User().setId(userId);
    }

    /**
     * @param command lệnh tạo đơn
     * @return số dòng giỏ hàng, dùng cho log
     */
    private int genItemCount(CreateOrderCommand command) {
        return command.getItems() == null ? 0 : command.getItems().size();
    }

    /**
     * Sinh {@code requestId} dạng {@code PR-<16 hex>} — cùng khuôn (SecureRandom, không query DB
     * trước) với {@code OrderDomainServiceImpl#genOrderCode}.
     *
     * @return khoá tự nhiên mới cho {@code purchase_request}
     */
    private String genRequestId() {
        StringBuilder id = new StringBuilder(REQUEST_ID_PREFIX.length() + REQUEST_ID_RANDOM_LENGTH);
        id.append(REQUEST_ID_PREFIX);
        for (int i = 0; i < REQUEST_ID_RANDOM_LENGTH; i++) {
            id.append(REQUEST_ID_ALPHABET.charAt(REQUEST_ID_RANDOM.nextInt(REQUEST_ID_ALPHABET.length())));
        }
        return id.toString();
    }
}
