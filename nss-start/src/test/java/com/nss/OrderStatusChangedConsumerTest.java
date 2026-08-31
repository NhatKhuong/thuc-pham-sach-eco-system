package com.nss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.service.mail.MailAppService;
import com.nss.ddd.application.service.order.mq.OrderStatusChangedConsumer;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.repository.IdempotencyKeyRepository;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.infrastructure.mq.OrderStatusChangedMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kiểm {@code OrderStatusChangedConsumer} — repository/mail đều mock, không cần Spring context lẫn
 * Kafka thật (backlog 0032 Phase 4).
 * <p>
 * <b>Ca đáng giá nhất ở đây là {@link #duplicateMessageDoesNotTriggerASecondEmail()}</b> — bằng
 * chứng idempotency ở mức đơn vị mà ticket đòi hỏi: gọi consumer <b>hai lần với cùng key</b>, lần
 * thứ hai không được gọi {@code MailAppService} lần nữa. Integration test đầy đủ (outbox → Kafka →
 * consumer, dùng {@code @EmbeddedKafka}) nằm ở {@code OrderStatusChangedOutboxIntegrationTest},
 * @Tag("db").
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusChangedConsumerTest {

    private static final Long EVENT_ID = 42L;

    private static final String KEY = "42";

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private OrderDomainService orderDomainService;

    @Mock
    private MailAppService mailAppService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrderStatusChangedConsumer genConsumer() {
        return new OrderStatusChangedConsumer(idempotencyKeyRepository, orderDomainService, mailAppService,
                objectMapper);
    }

    private String genPayload(String code, Integer fromStatus, Integer toStatus, String email) throws Exception {
        OrderStatusChangedMessage message = new OrderStatusChangedMessage()
                .setOrderId(1L)
                .setCode(code)
                .setFromStatus(fromStatus)
                .setToStatus(toStatus)
                .setShippingEmail(email)
                .setChangedAt("2026-08-31T10:30:00Z");
        return objectMapper.writeValueAsString(message);
    }

    // ========== IDEMPOTENCY ==========

    @Test
    @DisplayName("Duplicate (INSERT IGNORE tra false) khong doc don, khong goi mail")
    void duplicateMessageDoesNotTriggerASecondEmail() throws Exception {
        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(false);
        String payload = genPayload("NSS-20260831-K7M2QX9P4T", null, OrderDomainService.STATUS_PENDING,
                "khach@vidu.vn");

        genConsumer().onOrderStatusChanged(payload, KEY);

        verifyNoInteractions(orderDomainService, mailAppService);
    }

    @Test
    @DisplayName("Goi consumer HAI LAN voi CUNG key: lan hai bi chan o cong idempotency, mail chi goi 1 lan")
    void callingTwiceWithSameKeyOnlySendsOneEmail() throws Exception {
        Order order = new Order().setId(1L).setCode("NSS-20260831-K7M2QX9P4T")
                .setShipping(new com.nss.ddd.domain.model.entity.ShippingInfo().setEmail("khach@vidu.vn"));
        when(orderDomainService.findByCode("NSS-20260831-K7M2QX9P4T")).thenReturn(order);
        when(orderDomainService.findItemsGroupedByOrderId(List.of(1L))).thenReturn(Map.of(1L, List.of()));
        String payload = genPayload("NSS-20260831-K7M2QX9P4T", null, OrderDomainService.STATUS_PENDING,
                "khach@vidu.vn");

        // Lan 1: key moi -> tiep tuc xu ly va goi mail.
        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(true);
        genConsumer().onOrderStatusChanged(payload, KEY);

        // Lan 2 (redeliver / goi lai voi CUNG key): INSERT IGNORE tra false -> bo qua ngay,
        // KHONG duoc goi mail them lan nao nua.
        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(false);
        genConsumer().onOrderStatusChanged(payload, KEY);

        verify(mailAppService, times(1)).sendOrderStatusEmail(eq("khach@vidu.vn"), eq(order), any(),
                eq(OrderDomainService.STATUS_PENDING));
    }

    // ========== HAPPY PATH ==========

    @Test
    @DisplayName("Key moi: tra lai don, goi mail dung recipient va dung toStatus cua MESSAGE")
    void newKeyLooksUpOrderAndSendsMailWithMessageToStatus() throws Exception {
        Order order = new Order().setId(7L).setCode("NSS-20260831-ABCDEFGHJK")
                .setShipping(new com.nss.ddd.domain.model.entity.ShippingInfo().setEmail("khach@vidu.vn"));
        OrderItem item = new OrderItem().setName("Rau muong").setQuantity(2).setPrice(10_000L);
        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(true);
        when(orderDomainService.findByCode("NSS-20260831-ABCDEFGHJK")).thenReturn(order);
        when(orderDomainService.findItemsGroupedByOrderId(List.of(7L))).thenReturn(Map.of(7L, List.of(item)));
        // fromStatus=CONFIRMED, toStatus=SHIPPING: don co the da chuyen tiep sang trang thai khac
        // truoc khi consumer xu ly xong event NAY — mail phai hien thi dung SHIPPING, khong doc lai
        // order.getStatus() (co the da la mot gia tri moi hon).
        String payload = genPayload("NSS-20260831-ABCDEFGHJK", OrderDomainService.STATUS_CONFIRMED,
                OrderDomainService.STATUS_SHIPPING, "khach@vidu.vn");

        genConsumer().onOrderStatusChanged(payload, "42");

        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mailAppService).sendOrderStatusEmail(eq("khach@vidu.vn"), eq(order), itemsCaptor.capture(),
                eq(OrderDomainService.STATUS_SHIPPING));
        assertEquals(1, itemsCaptor.getValue().size());
        assertEquals("Rau muong", itemsCaptor.getValue().get(0).getName());
    }

    @Test
    @DisplayName("Don khong con ton tai: idempotency da chen, khong goi mail, khong nem loi")
    void orderNotFoundSkipsMailWithoutThrowing() throws Exception {
        when(idempotencyKeyRepository.tryInsert(eq(EVENT_ID), any())).thenReturn(true);
        when(orderDomainService.findByCode(anyString())).thenReturn(null);
        String payload = genPayload("NSS-KHONG-TON-TAI", null, OrderDomainService.STATUS_PENDING, "a@b.vn");

        genConsumer().onOrderStatusChanged(payload, KEY);

        verify(mailAppService, never()).sendOrderStatusEmail(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("eventId lay tu Kafka record key (chuoi hoa cua outbox event id), khong tu payload")
    void eventIdComesFromKafkaKeyNotFromPayload() throws Exception {
        when(idempotencyKeyRepository.tryInsert(anyLong(), any())).thenReturn(false);
        String payload = genPayload("NSS-X", null, OrderDomainService.STATUS_PENDING, "a@b.vn");

        genConsumer().onOrderStatusChanged(payload, "999");

        ArgumentCaptor<Long> eventIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(idempotencyKeyRepository).tryInsert(eventIdCaptor.capture(), any());
        assertEquals(999L, eventIdCaptor.getValue());
    }
}
