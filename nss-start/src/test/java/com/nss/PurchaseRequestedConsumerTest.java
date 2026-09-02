package com.nss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.model.response.OrderResponse;
import com.nss.ddd.application.service.order.OrderAppService;
import com.nss.ddd.application.service.purchaserequest.mq.PurchaseRequestedConsumer;
import com.nss.ddd.domain.repository.IdempotencyKeyRepository;
import com.nss.ddd.domain.repository.PurchaseRequestRepository;
import com.nss.ddd.infrastructure.mq.PurchaseRequestedItemMessage;
import com.nss.ddd.infrastructure.mq.PurchaseRequestedMessage;
import com.nss.ddd.infrastructure.mq.PurchaseRequestedShippingMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kiểm {@code PurchaseRequestedConsumer} — repository/service đều mock, không cần Spring context
 * lẫn Kafka thật (backlog 0039 Phase 5). Cùng khuôn {@code OrderStatusChangedConsumerTest}.
 * <p>
 * <b>Ca đáng giá nhất ở đây là {@link #duplicateEventIdDoesNotCreateOrderAgain()}</b> — bằng chứng
 * idempotency ở mức đơn vị: gọi consumer <b>hai lần với cùng eventId (header)</b>, lần thứ hai không
 * được gọi {@code OrderAppService} lần nữa. Bằng chứng ở mức DB thật (redeliver Kafka thật, không
 * chỉ gọi Java hai lần) nằm ở {@code PurchaseRequestedOutboxIntegrationTest}, {@code @Tag("db")}.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseRequestedConsumerTest {

    private static final Long EVENT_ID = 42L;

    private static final String EVENT_ID_HEADER = "42";

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private OrderAppService orderAppService;

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PurchaseRequestedConsumer genConsumer() {
        return new PurchaseRequestedConsumer(idempotencyKeyRepository, orderAppService, purchaseRequestRepository,
                objectMapper);
    }

    private String genPayload(String requestId, Long userId) throws Exception {
        PurchaseRequestedMessage message = new PurchaseRequestedMessage()
                .setRequestId(requestId)
                .setUserId(userId)
                .setItems(List.of(new PurchaseRequestedItemMessage()
                        .setProductId(9L).setName("Rau muống").setQuantity(2).setPrice(10_000L)))
                .setShipping(new PurchaseRequestedShippingMessage()
                        .setFullName("Nguyễn Văn A").setPhone("0900000000").setEmail("khach@vidu.vn")
                        .setProvince("HCM").setDistrict("Q1").setWard("P.Bến Nghé").setStreet("1 Lê Lợi"))
                .setPaymentMethod("cod");
        return objectMapper.writeValueAsString(message);
    }

    // ========== IDEMPOTENCY ==========

    @Test
    @DisplayName("Duplicate (INSERT IGNORE tra false) khong goi OrderAppService, khong ghi purchase_request")
    void duplicateEventIdSkipsProcessing() throws Exception {
        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(false);
        String payload = genPayload("PR-1111111111111111", null);

        genConsumer().onPurchaseRequested(payload, EVENT_ID_HEADER);

        verifyNoInteractions(orderAppService, purchaseRequestRepository);
    }

    @Test
    @DisplayName("Goi consumer HAI LAN voi CUNG eventId: lan hai bi chan o cong idempotency, tao don CHI 1 lan")
    void duplicateEventIdDoesNotCreateOrderAgain() throws Exception {
        String payload = genPayload("PR-2222222222222222", 9L);
        when(orderAppService.createOrderInNewTransaction(any()))
                .thenReturn(OrderMutationResponse.success(new OrderResponse().setCode("NSS-X")));

        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(true);
        genConsumer().onPurchaseRequested(payload, EVENT_ID_HEADER);

        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(false);
        genConsumer().onPurchaseRequested(payload, EVENT_ID_HEADER);

        verify(orderAppService, org.mockito.Mockito.times(1)).createOrderInNewTransaction(any());
    }

    // ========== HAPPY PATH ==========

    @Test
    @DisplayName("Thanh cong: goi createOrderInNewTransaction (KHONG createOrder thuong), markSuccess dung orderCode")
    void successCallsNewTransactionVariantAndMarksSuccess() throws Exception {
        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(true);
        String payload = genPayload("PR-3333333333333333", 9L);
        OrderResponse orderResponse = new OrderResponse().setCode("NSS-20260902-K7M2QX9P4T");
        when(orderAppService.createOrderInNewTransaction(any()))
                .thenReturn(OrderMutationResponse.success(orderResponse));

        genConsumer().onPurchaseRequested(payload, EVENT_ID_HEADER);

        ArgumentCaptor<CreateOrderCommand> commandCaptor = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderAppService).createOrderInNewTransaction(commandCaptor.capture());
        assertEquals(9L, commandCaptor.getValue().getUserId());
        assertEquals(1, commandCaptor.getValue().getItems().size());
        verify(orderAppService, never()).createOrder(any());

        verify(purchaseRequestRepository).markSuccess(eq("PR-3333333333333333"), eq("NSS-20260902-K7M2QX9P4T"), any());
        verify(purchaseRequestRepository, never()).markFailed(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("That bai nghiep vu (gia tri tra ve, khong exception): markFailed dung code/message")
    void businessFailureMarksFailedWithCodeAndMessage() throws Exception {
        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(true);
        String payload = genPayload("PR-4444444444444444", null);
        when(orderAppService.createOrderInNewTransaction(any())).thenReturn(
                OrderMutationResponse.failed(OrderMutationResponse.CODE_OUT_OF_STOCK,
                        "Sản phẩm đã hết hàng, vui lòng chọn sản phẩm khác."));

        genConsumer().onPurchaseRequested(payload, EVENT_ID_HEADER);

        verify(purchaseRequestRepository).markFailed(eq("PR-4444444444444444"),
                eq(OrderMutationResponse.CODE_OUT_OF_STOCK),
                eq("Sản phẩm đã hết hàng, vui lòng chọn sản phẩm khác."), any());
        verify(purchaseRequestRepository, never()).markSuccess(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("eventId lay tu HEADER X-Event-Id, khong tu record key/payload")
    void eventIdComesFromHeaderNotFromPayloadOrKey() throws Exception {
        when(idempotencyKeyRepository.tryInsert(any(), any())).thenReturn(false);
        String payload = genPayload("PR-5555555555555555", null);

        genConsumer().onPurchaseRequested(payload, "999");

        ArgumentCaptor<Long> eventIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(idempotencyKeyRepository).tryInsert(eventIdCaptor.capture(), any());
        assertEquals(999L, eventIdCaptor.getValue());
    }
}
