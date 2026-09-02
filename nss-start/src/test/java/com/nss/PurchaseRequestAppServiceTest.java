package com.nss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.mapper.PurchaseRequestMapper;
import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.application.model.response.PurchaseRequestResponse;
import com.nss.ddd.application.service.purchaserequest.PurchaseRequestAppService;
import com.nss.ddd.application.service.purchaserequest.impl.PurchaseRequestAppServiceImpl;
import com.nss.ddd.domain.model.entity.OutboxEvent;
import com.nss.ddd.domain.model.entity.PurchaseRequest;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.domain.repository.PurchaseRequestRepository;
import com.nss.ddd.infrastructure.config.KafkaTopicConfig;
import com.nss.ddd.infrastructure.mq.PurchaseRequestedMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm {@code PurchaseRequestAppServiceImpl} (backlog 0039 Phase 4) — repository đều mock,
 * {@code ObjectMapper} là bản THẬT để bằng chứng JSON không phải "tin vào mock trả đúng", cùng
 * khuôn {@code OrderAppServiceOutboxEventTest}.
 */
class PurchaseRequestAppServiceTest {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^PR-[0-9a-f]{16}$");

    private final PurchaseRequestRepository purchaseRequestRepository = mock(PurchaseRequestRepository.class);

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PurchaseRequestAppService purchaseRequestAppService =
            new PurchaseRequestAppServiceImpl(purchaseRequestRepository, outboxEventRepository, objectMapper);

    private CreateOrderCommand genCommand(Long productId) {
        return new CreateOrderCommand()
                .setUserId(9L)
                .setItems(List.of(new CartItemCommand()
                        .setProductId(productId).setName("Rau muống").setQuantity(2).setPrice(10_000L)))
                .setShipping(new ShippingInfoCommand()
                        .setFullName("Nguyễn Văn A").setPhone("0900000000").setEmail("khach@vidu.vn")
                        .setProvince("HCM").setDistrict("Q1").setWard("P.Bến Nghé").setStreet("1 Lê Lợi"))
                .setPaymentMethod("cod");
    }

    // ========== SUBMIT — KEY MOI ==========

    @Test
    @DisplayName("Key moi: tryInsert PENDING, ghi DUNG 1 outbox_event, tra ve status=PENDING")
    void freshIdempotencyKeyInsertsAndWritesOutboxEvent() throws Exception {
        when(purchaseRequestRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(purchaseRequestRepository.tryInsert(any())).thenReturn(true);

        PurchaseRequestResponse response = purchaseRequestAppService.submitAsync(genCommand(9L), "idem-1");

        assertEquals("PENDING", response.getStatus());
        assertTrue(REQUEST_ID_PATTERN.matcher(response.getRequestId()).matches(),
                "requestId phai dang PR-<16 hex>, nhan duoc: " + response.getRequestId());
        assertNull(response.getOrderCode());
        assertNull(response.getFailureCode());

        ArgumentCaptor<PurchaseRequest> draftCaptor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseRequestRepository).tryInsert(draftCaptor.capture());
        assertEquals("idem-1", draftCaptor.getValue().getIdempotencyKey());
        assertEquals(9L, draftCaptor.getValue().getUser().getId());
        assertEquals(PurchaseRequest.STATUS_PENDING, draftCaptor.getValue().getStatus());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        assertEquals(response.getRequestId(), event.getAggregateId());
        assertEquals(KafkaTopicConfig.EVENT_TYPE_PURCHASE_REQUESTED, event.getEventType());
        assertEquals(OutboxEvent.STATUS_PENDING, event.getStatus());
        assertEquals("9", event.getPartitionKey(), "partition key phai la productId cua dong DAU TIEN");

        PurchaseRequestedMessage message = objectMapper.readValue(event.getPayload(), PurchaseRequestedMessage.class);
        assertEquals(response.getRequestId(), message.getRequestId());
        assertEquals(9L, message.getUserId());
        assertEquals(1, message.getItems().size());
        assertEquals(9L, message.getItems().get(0).getProductId());
        assertEquals("cod", message.getPaymentMethod());
    }

    @Test
    @DisplayName("Gio hang rong: partition key la null (OutboxPublisherJob se fallback ve id)")
    void emptyCartYieldsNullPartitionKey() {
        when(purchaseRequestRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(purchaseRequestRepository.tryInsert(any())).thenReturn(true);
        CreateOrderCommand emptyCommand = genCommand(9L).setItems(List.of());

        purchaseRequestAppService.submitAsync(emptyCommand, "idem-empty");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertNull(outboxCaptor.getValue().getPartitionKey());
    }

    // ========== IDEMPOTENT REPLAY ==========

    @Test
    @DisplayName("Key da ton tai (doc truoc thay duoc ngay): tra lai DUNG requestId/status cu, KHONG tao outbox moi")
    void existingIdempotencyKeyReplaysWithoutNewOutboxEvent() {
        PurchaseRequest existing = new PurchaseRequest()
                .setRequestId("PR-aaaaaaaaaaaaaaaa")
                .setIdempotencyKey("idem-2")
                .setStatus(PurchaseRequest.STATUS_SUCCESS)
                .setOrderCode("NSS-20260902-ABCDEFGHJK")
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        when(purchaseRequestRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.of(existing));

        PurchaseRequestResponse response = purchaseRequestAppService.submitAsync(genCommand(9L), "idem-2");

        assertEquals("PR-aaaaaaaaaaaaaaaa", response.getRequestId());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("NSS-20260902-ABCDEFGHJK", response.getOrderCode());
        verify(purchaseRequestRepository, never()).tryInsert(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Thua race dieu kien (tryInsert tra false): doc lai ban ghi cua nguoi thang, KHONG tao outbox")
    void lostRaceReadsWinnerRowWithoutCreatingOutbox() {
        PurchaseRequest winner = new PurchaseRequest()
                .setRequestId("PR-bbbbbbbbbbbbbbbb")
                .setIdempotencyKey("idem-3")
                .setStatus(PurchaseRequest.STATUS_PENDING)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        // Lan doc dau (fast path) chua thay — hai request dong thoi cung vao nhanh nay.
        when(purchaseRequestRepository.findByIdempotencyKey("idem-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(purchaseRequestRepository.tryInsert(any())).thenReturn(false);

        PurchaseRequestResponse response = purchaseRequestAppService.submitAsync(genCommand(9L), "idem-3");

        assertEquals("PR-bbbbbbbbbbbbbbbb", response.getRequestId());
        assertEquals("PENDING", response.getStatus());
        verify(outboxEventRepository, never()).save(any());
    }

    // ========== POLLING ==========

    @Test
    @DisplayName("findByRequestId: khong ton tai tra ve null (controller se dich thanh 404)")
    void findByRequestIdReturnsNullWhenMissing() {
        when(purchaseRequestRepository.findByRequestId("PR-khongtontai")).thenReturn(Optional.empty());

        assertNull(purchaseRequestAppService.findByRequestId("PR-khongtontai"));
    }

    @Test
    @DisplayName("findByRequestId: FAILED map dung failureCode/failureMessage, orderCode null")
    void findByRequestIdMapsFailedStatusCorrectly() {
        PurchaseRequest failed = new PurchaseRequest()
                .setRequestId("PR-cccccccccccccccc")
                .setStatus(PurchaseRequest.STATUS_FAILED)
                .setFailureCode("OUT_OF_STOCK")
                .setFailureMessage("Sản phẩm đã hết hàng.")
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        when(purchaseRequestRepository.findByRequestId("PR-cccccccccccccccc")).thenReturn(Optional.of(failed));

        PurchaseRequestResponse response = purchaseRequestAppService.findByRequestId("PR-cccccccccccccccc");

        assertEquals("FAILED", response.getStatus());
        assertEquals("OUT_OF_STOCK", response.getFailureCode());
        assertEquals("Sản phẩm đã hết hàng.", response.getFailureMessage());
        assertNull(response.getOrderCode());
    }

    @Test
    @DisplayName("PurchaseRequestMapper.toWireStatus: ma la khong ro rang tra null, khong roi ve mac dinh sai")
    void unknownStatusCodeMapsToNull() {
        assertNull(PurchaseRequestMapper.toWireStatus(99));
        assertNull(PurchaseRequestMapper.toWireStatus(null));
    }
}
