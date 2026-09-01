package com.nss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.service.order.OrderAppService;
import com.nss.ddd.application.service.order.impl.OrderAppServiceImpl;
import com.nss.ddd.application.service.product.cache.StockCacheService;
import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.domain.service.CouponDomainService;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.domain.service.ProductDomainService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Compensation Redis (Tầng 1) cho <b>từng nhánh lỗi</b> của {@code OrderAppServiceImpl.createOrder}
 * sau backlog 0035 Phase 2 (architecture/01-overview.md §5 Tầng 3 — SAGA compensation bắt buộc).
 * <p>
 * <b>Mọi kịch bản đều dựng giỏ HAI dòng, cả hai đều trừ Lua (Tầng 1) thành công</b>, rồi cho lỗi xảy
 * ra ở một bước SAU vòng lặp trừ kho (hoặc ở chính dòng thứ hai của vòng lặp) — điểm cần khẳng định
 * là {@code stockCacheService.increaseStock} được gọi cho <b>CẢ HAI</b> dòng đã trừ, không chỉ dòng
 * (nếu có) trực tiếp gây ra lỗi. Đây đúng là khe hở mà javadoc {@code OrderAppServiceImpl} gọi là
 * "cách dễ nhất để phá hệ thống này" nếu quên.
 */
class OrderAppServiceStockCompensationTest {

    private static final Long PRODUCT_1 = 1L;
    private static final int QUANTITY_1 = 2;
    private static final Long PRODUCT_2 = 2L;
    private static final int QUANTITY_2 = 3;

    private final OrderDomainService orderDomainService = mock(OrderDomainService.class);

    private final CouponDomainService couponDomainService = mock(CouponDomainService.class);

    private final ProductDomainService productDomainService = mock(ProductDomainService.class);

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StockCacheService stockCacheService = mock(StockCacheService.class);

    /**
     * <b>Proxy transaction thật, không phải {@code new OrderAppServiceImpl(...)} trần.</b>
     * {@code failedAndRollback} gọi {@code TransactionAspectSupport.currentTransactionStatus()}, thứ
     * chỉ tồn tại khi lời gọi đi qua đúng interceptor {@code @Transactional} của Spring AOP — gọi
     * method Java trần (không proxy) ném {@code NoTransactionException} ngay lập tức. Dựng một
     * {@link TransactionInterceptor} thật với một {@link AbstractPlatformTransactionManager}
     * "không làm gì" (không cần DataSource) là cách rẻ nhất tái tạo đúng ngữ cảnh đó trong unit test.
     * {@code setProxyTargetClass(true)} bắt buộc: {@code @Transactional} khai trên method của
     * {@code OrderAppServiceImpl} (class), không khai trên {@code OrderAppService} (interface), nên
     * phải proxy CGLIB trên chính class thì {@code AnnotationTransactionAttributeSource} mới đọc
     * được annotation.
     */
    private final OrderAppService orderAppService = genTransactionalProxy(new OrderAppServiceImpl(
            orderDomainService, couponDomainService, productDomainService, outboxEventRepository,
            objectMapper, stockCacheService));

    private static OrderAppService genTransactionalProxy(OrderAppServiceImpl target) {
        AbstractPlatformTransactionManager noOpTransactionManager = new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                // Khong can tai nguyen that — chi can mot TransactionStatus hop le de setRollbackOnly() khong nem loi.
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
        TransactionInterceptor interceptor =
                new TransactionInterceptor(noOpTransactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(interceptor);
        return (OrderAppService) proxyFactory.getProxy();
    }

    /** Giỏ hai dòng, cả hai đều trừ Lua (Tầng 1) thành công — kịch bản chung cho mọi test dưới đây. */
    private CreateOrderCommand genTwoLineCommand() {
        Product product1 = new Product().setId(PRODUCT_1).setSlug("rau-muong").setName("Rau muống")
                .setUnit("bó").setPrice(10_000L).setEffectivePrice(10_000L).setStock(50);
        Product product2 = new Product().setId(PRODUCT_2).setSlug("ca-rot").setName("Cà rốt")
                .setUnit("kg").setPrice(20_000L).setEffectivePrice(20_000L).setStock(50);
        when(orderDomainService.findProductsByIds(any())).thenReturn(Map.of(PRODUCT_1, product1, PRODUCT_2, product2));
        when(productDomainService.findImagesGroupedByProductId(any())).thenReturn(Map.of());
        when(stockCacheService.deductStock(eq(PRODUCT_1), anyInt(), anyLong())).thenReturn(StockCacheService.DEDUCTED);
        when(stockCacheService.deductStock(eq(PRODUCT_2), anyInt(), anyLong())).thenReturn(StockCacheService.DEDUCTED);

        return new CreateOrderCommand()
                .setUserId(null)
                .setItems(List.of(
                        new CartItemCommand().setProductId(PRODUCT_1).setName("Rau muống").setQuantity(QUANTITY_1),
                        new CartItemCommand().setProductId(PRODUCT_2).setName("Cà rốt").setQuantity(QUANTITY_2)))
                .setShipping(new ShippingInfoCommand()
                        .setFullName("Nguyễn Văn A").setPhone("0900000000").setEmail("khach@vidu.vn")
                        .setProvince("HCM").setDistrict("Q1").setWard("P.Bến Nghé").setStreet("1 Lê Lợi"))
                .setPaymentMethod("cod");
    }

    private void assertBothLinesCompensatedExactlyOnce() {
        verify(stockCacheService, times(1)).increaseStock(PRODUCT_1, QUANTITY_1);
        verify(stockCacheService, times(1)).increaseStock(PRODUCT_2, QUANTITY_2);
    }

    @Test
    @DisplayName("Tang 2 tu choi o dong thu hai -> compensate CA HAI dong da tru Lua, khong chi dong loi")
    void tier2FailureOnSecondLineCompensatesBothLines() {
        CreateOrderCommand command = genTwoLineCommand();
        when(orderDomainService.deductStock(PRODUCT_1, QUANTITY_1)).thenReturn(true);
        when(orderDomainService.deductStock(PRODUCT_2, QUANTITY_2)).thenReturn(false);

        OrderMutationResponse result = orderAppService.createOrder(command);

        assertNull(result.getOrder());
        assertEquals(OrderMutationResponse.CODE_OUT_OF_STOCK, result.getCode());
        assertBothLinesCompensatedExactlyOnce();
    }

    @Test
    @DisplayName("Ma giam gia khong ton tai -> compensate CA HAI dong da tru Lua")
    void couponNotFoundCompensatesBothLines() {
        CreateOrderCommand command = genTwoLineCommand().setCouponCode("KHONGTONTAI");
        when(orderDomainService.deductStock(PRODUCT_1, QUANTITY_1)).thenReturn(true);
        when(orderDomainService.deductStock(PRODUCT_2, QUANTITY_2)).thenReturn(true);
        when(couponDomainService.findByCode("KHONGTONTAI")).thenReturn(null);

        OrderMutationResponse result = orderAppService.createOrder(command);

        assertNull(result.getOrder());
        assertBothLinesCompensatedExactlyOnce();
    }

    @Test
    @DisplayName("Ma giam gia khong con hieu luc -> compensate CA HAI dong da tru Lua")
    void couponNotRedeemableCompensatesBothLines() {
        CreateOrderCommand command = genTwoLineCommand().setCouponCode("HETHAN");
        when(orderDomainService.deductStock(PRODUCT_1, QUANTITY_1)).thenReturn(true);
        when(orderDomainService.deductStock(PRODUCT_2, QUANTITY_2)).thenReturn(true);
        Coupon coupon = new Coupon().setCode("HETHAN");
        when(couponDomainService.findByCode("HETHAN")).thenReturn(coupon);
        when(couponDomainService.isRedeemable(coupon)).thenReturn(false);

        OrderMutationResponse result = orderAppService.createOrder(command);

        assertNull(result.getOrder());
        assertBothLinesCompensatedExactlyOnce();
    }

    @Test
    @DisplayName("Don chua dat gia tri toi thieu cua ma -> compensate CA HAI dong da tru Lua")
    void belowMinOrderValueCompensatesBothLines() {
        CreateOrderCommand command = genTwoLineCommand().setCouponCode("MIN1TRIEU");
        when(orderDomainService.deductStock(PRODUCT_1, QUANTITY_1)).thenReturn(true);
        when(orderDomainService.deductStock(PRODUCT_2, QUANTITY_2)).thenReturn(true);
        Coupon coupon = new Coupon().setCode("MIN1TRIEU").setMinOrderValue(1_000_000L);
        when(couponDomainService.findByCode("MIN1TRIEU")).thenReturn(coupon);
        when(couponDomainService.isRedeemable(coupon)).thenReturn(true);
        when(couponDomainService.meetsMinOrderValue(eq(coupon), anyLong())).thenReturn(false);

        OrderMutationResponse result = orderAppService.createOrder(command);

        assertNull(result.getOrder());
        assertBothLinesCompensatedExactlyOnce();
    }

    @Test
    @DisplayName("Ma giam gia vua het luot (redeemCoupon that bai) -> compensate CA HAI dong da tru Lua")
    void couponUsageLimitReachedCompensatesBothLines() {
        CreateOrderCommand command = genTwoLineCommand().setCouponCode("HETLUOT");
        when(orderDomainService.deductStock(PRODUCT_1, QUANTITY_1)).thenReturn(true);
        when(orderDomainService.deductStock(PRODUCT_2, QUANTITY_2)).thenReturn(true);
        Coupon coupon = new Coupon().setCode("HETLUOT");
        when(couponDomainService.findByCode("HETLUOT")).thenReturn(coupon);
        when(couponDomainService.isRedeemable(coupon)).thenReturn(true);
        when(couponDomainService.meetsMinOrderValue(eq(coupon), anyLong())).thenReturn(true);
        when(orderDomainService.redeemCoupon("HETLUOT")).thenReturn(false);

        OrderMutationResponse result = orderAppService.createOrder(command);

        assertNull(result.getOrder());
        assertBothLinesCompensatedExactlyOnce();
    }

    @Test
    @DisplayName("Exception ngoai du kien luc INSERT don (buoc 4-6) -> compensate CA HAI dong roi rethrow")
    void unexpectedExceptionDuringWriteCompensatesBothLinesThenRethrows() {
        CreateOrderCommand command = genTwoLineCommand();
        when(orderDomainService.deductStock(PRODUCT_1, QUANTITY_1)).thenReturn(true);
        when(orderDomainService.deductStock(PRODUCT_2, QUANTITY_2)).thenReturn(true);
        when(orderDomainService.genOrderCode(any())).thenReturn("NSS-20260901-EXCEPTIONTEST");
        when(orderDomainService.create(any())).thenThrow(new IllegalStateException("loi ha tang gia lap"));

        assertThrows(IllegalStateException.class, () -> orderAppService.createOrder(command));

        assertBothLinesCompensatedExactlyOnce();
        verify(outboxEventRepository, org.mockito.Mockito.never()).save(any());
    }
}
