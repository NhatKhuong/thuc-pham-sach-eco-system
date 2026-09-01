package com.nss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.application.service.order.OrderAppService;
import com.nss.ddd.application.service.order.impl.OrderAppServiceImpl;
import com.nss.ddd.application.service.product.cache.StockCacheService;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OutboxEvent;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.domain.service.CouponDomainService;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.domain.service.ProductDomainService;
import com.nss.ddd.infrastructure.mq.OrderStatusChangedMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm việc sinh {@code outbox_event} tại 2 điểm ghi trạng thái của {@code OrderAppServiceImpl}
 * (backlog 0032 Phase 2) — repository domain đều mock, {@code ObjectMapper} là bản THẬT để bằng
 * chứng JSON không phải "tin vào mock trả đúng".
 * <p>
 * <b>Không kiểm cổng nghiệp vụ đã có test riêng</b> ({@code OrderDomainServiceTest}) — chỉ kiểm
 * đúng MỘT thứ mới: mỗi lần {@code createOrder}/{@code changeOrderStatus} thành công thì có đúng
 * MỘT dòng {@code outbox_event} với payload đúng hình dạng §Contract của ticket.
 */
class OrderAppServiceOutboxEventTest {

    private final OrderDomainService orderDomainService = mock(OrderDomainService.class);

    private final CouponDomainService couponDomainService = mock(CouponDomainService.class);

    private final ProductDomainService productDomainService = mock(ProductDomainService.class);

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Mock trần, không stub {@code deductStock} — Mockito trả {@code 0} (int) mặc định, tức
     * {@code StockCacheService.INSUFFICIENT}. Không kích hoạt warm/retry (chỉ {@code MISS} mới kích
     * hoạt) và không được thêm vào danh sách compensate (chỉ {@code DEDUCTED} mới được thêm) — Tầng 2
     * (đã mock ở dưới) vẫn là trọng tài quyết định thành/bại, đúng ý {@code createOrder} sau backlog
     * 0035 Phase 2.
     */
    private final StockCacheService stockCacheService = mock(StockCacheService.class);

    private final OrderAppService orderAppService = new OrderAppServiceImpl(orderDomainService,
            couponDomainService, productDomainService, outboxEventRepository, objectMapper, stockCacheService);

    @Test
    @DisplayName("createOrder thanh cong -> dung 1 outbox_event, fromStatus=null, payload dung hinh dang")
    void createOrderWritesExactlyOneOutboxEventWithNullFromStatus() throws Exception {
        Product product = new Product().setId(9L).setSlug("rau-muong").setName("Rau muống")
                .setUnit("bó").setPrice(10_000L).setEffectivePrice(10_000L);
        when(orderDomainService.findProductsByIds(any())).thenReturn(Map.of(9L, product));
        when(productDomainService.findImagesGroupedByProductId(any())).thenReturn(Map.of());
        when(orderDomainService.deductStock(eq(9L), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(orderDomainService.genOrderCode(any())).thenReturn("NSS-20260831-K7M2QX9P4T");
        when(orderDomainService.create(any())).thenAnswer(inv -> inv.getArgument(0, Order.class).setId(100L));
        when(orderDomainService.createItems(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand()
                .setUserId(null)
                .setItems(List.of(new CartItemCommand().setProductId(9L).setName("Rau muống").setQuantity(2)))
                .setShipping(new ShippingInfoCommand()
                        .setFullName("Nguyễn Văn A").setPhone("0900000000").setEmail("khach@vidu.vn")
                        .setProvince("HCM").setDistrict("Q1").setWard("P.Bến Nghé").setStreet("1 Lê Lợi"))
                .setPaymentMethod("cod");

        orderAppService.createOrder(command);

        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals("NSS-20260831-K7M2QX9P4T", event.getAggregateId());
        assertEquals("OrderStatusChanged", event.getEventType());
        assertEquals(OutboxEvent.STATUS_PENDING, event.getStatus());

        OrderStatusChangedMessage message = objectMapper.readValue(event.getPayload(), OrderStatusChangedMessage.class);
        assertEquals(100L, message.getOrderId());
        assertEquals("NSS-20260831-K7M2QX9P4T", message.getCode());
        assertNull(message.getFromStatus(), "don vua tao khong di TU dau ca");
        assertEquals(OrderDomainService.STATUS_PENDING, message.getToStatus());
        assertEquals("khach@vidu.vn", message.getShippingEmail());
        assertTrue(message.getChangedAt().endsWith("Z"), "changedAt phai la ISO-8601 UTC: " + message.getChangedAt());
    }

    @Test
    @DisplayName("changeOrderStatus thanh cong -> dung 1 outbox_event voi fromStatus/toStatus dung")
    void changeOrderStatusWritesOutboxEventWithBothStatuses() throws Exception {
        Order existing = new Order().setId(5L).setCode("NSS-20260831-ABCDEFGHJK")
                .setStatus(OrderDomainService.STATUS_PENDING)
                .setShipping(new com.nss.ddd.domain.model.entity.ShippingInfo().setEmail("khach2@vidu.vn"));
        when(orderDomainService.findByCode("NSS-20260831-ABCDEFGHJK")).thenReturn(existing);
        when(orderDomainService.canTransition(OrderDomainService.STATUS_PENDING, OrderDomainService.STATUS_CANCELLED))
                .thenReturn(true);
        when(orderDomainService.updateStatus(any(), eq(OrderDomainService.STATUS_CANCELLED), any()))
                .thenAnswer(inv -> existing.setStatus(OrderDomainService.STATUS_CANCELLED));
        when(orderDomainService.findItemsGroupedByOrderId(any())).thenReturn(Map.of());

        orderAppService.changeOrderStatus("NSS-20260831-ABCDEFGHJK", "cancelled", "1");

        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals("NSS-20260831-ABCDEFGHJK", event.getAggregateId());

        OrderStatusChangedMessage message = objectMapper.readValue(event.getPayload(), OrderStatusChangedMessage.class);
        assertEquals(OrderDomainService.STATUS_PENDING, message.getFromStatus());
        assertEquals(OrderDomainService.STATUS_CANCELLED, message.getToStatus(),
                "phai bat duoc CA trang thai CANCELLED — 'moi trang thai' bao gom ca huy don");
        assertEquals("khach2@vidu.vn", message.getShippingEmail());
    }

    @Test
    @DisplayName("Transition bi tu choi: KHONG ghi outbox_event nao ca")
    void rejectedTransitionWritesNoOutboxEvent() {
        Order existing = new Order().setId(5L).setCode("NSS-X")
                .setStatus(OrderDomainService.STATUS_DELIVERED)
                .setShipping(new com.nss.ddd.domain.model.entity.ShippingInfo().setEmail("a@b.vn"));
        when(orderDomainService.findByCode("NSS-X")).thenReturn(existing);
        when(orderDomainService.canTransition(OrderDomainService.STATUS_DELIVERED, OrderDomainService.STATUS_PENDING))
                .thenReturn(false);

        orderAppService.changeOrderStatus("NSS-X", "pending", "1");

        verify(outboxEventRepository, org.mockito.Mockito.never()).save(any());
    }
}
