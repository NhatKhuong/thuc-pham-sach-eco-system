package com.nss;

import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.OrderStatusHistory;
import com.nss.ddd.domain.repository.CouponRepository;
import com.nss.ddd.domain.repository.OrderRepository;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.repository.UserRepository;
import com.nss.ddd.domain.service.CouponDomainService;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.domain.service.impl.OrderDomainServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm {@code OrderDomainServiceImpl} — phép tính tiền thuần, repository là mock, không cần database.
 * <p>
 * <b>Ba nhóm ca ở đây khoá lại ba chỗ mà backlog 0014 nói thẳng là "sai mà không có lỗi nào nổ ra":</b>
 * <ul>
 *   <li><b>Ngưỡng vận chuyển tính trên số ĐÃ trừ giảm giá</b> (§Contract 1). Tính trên
 *       {@code subtotal} trần cho ra một hệ thống chạy hoàn hảo, chỉ lệch 30.000 ₫ ở những đơn
 *       quanh ngưỡng — và lệch so với con số frontend vừa hiện cho khách.</li>
 *   <li><b>Làm tròn HALF_UP</b> (§Contract 3). {@code HALF_EVEN} chỉ khác ở đúng những giá trị rơi
 *       vào ranh giới nửa đồng, nên một bộ test chọn số tròn sẽ xanh với cả hai quy ước.</li>
 *   <li><b>Khuôn mã đơn</b> (§Contract 6). Cột {@code code} có {@code uk_code}, nên một khuôn sai
 *       chỉ lộ ra khi hai đơn đụng nhau.</li>
 * </ul>
 */
class OrderDomainServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);

    private final ProductRepository productRepository = mock(ProductRepository.class);

    private final CouponRepository couponRepository = mock(CouponRepository.class);

    private final UserRepository userRepository = mock(UserRepository.class);

    private final OrderDomainService orderDomainService = new OrderDomainServiceImpl(
            orderRepository, productRepository, couponRepository, userRepository);

    // ========== SUBTOTAL: CHI TU GIA CUA DB ==========

    @Test
    @DisplayName("calcSubtotal cong don gia x so luong tren tung dong")
    void subtotalSumsLineAmounts() {
        long subtotal = orderDomainService.calcSubtotal(List.of(
                new OrderItem().setPrice(449_000L).setQuantity(2),
                new OrderItem().setPrice(39_000L).setQuantity(3)));

        assertEquals(1_015_000L, subtotal);
    }

    @Test
    @DisplayName("calcSubtotal cua gio rong va gio null deu ra 0")
    void subtotalOfEmptyCartIsZero() {
        assertEquals(0L, orderDomainService.calcSubtotal(List.of()));
        assertEquals(0L, orderDomainService.calcSubtotal(null));
    }

    // ========== DISCOUNT: HALF_UP LA CONTRACT ==========

    /**
     * Mã {@code percent} làm tròn <b>HALF_UP</b>, không phải HALF_EVEN (§Contract 3).
     * <p>
     * <b>Bốn con số dưới đây chọn để phân biệt được hai quy ước, không phải chọn cho tròn.</b>
     * {@code subtotal = 105} với mã 5% cho ra đúng {@code 5.25} — không phải ranh giới, nên nó chỉ
     * kiểm phép nhân. Ba ca còn lại rơi <i>đúng</i> vào {@code .5}: HALF_UP làm tròn lên,
     * HALF_EVEN làm tròn về số chẵn gần nhất. {@code 1050 * 5% = 52.5} là ca sắc nhất —
     * HALF_UP ra {@code 53}, HALF_EVEN ra {@code 52}. Một bộ test chỉ dùng số tròn sẽ xanh với cả
     * hai quy ước và không bảo vệ được gì.
     *
     * @param subtotal tổng tiền hàng
     * @param percent phần trăm giảm
     * @param expected số tiền giảm mong đợi theo HALF_UP
     */
    @ParameterizedTest(name = "subtotal={0} giam {1}% -> discount={2} (HALF_UP)")
    @CsvSource({
            "105,   5,  5",
            "1050,  5,  53",
            "1250,  5,  63",
            "1450,  5,  73",
            "500000, 10, 50000"
    })
    @DisplayName("calcDiscount cua ma percent lam tron HALF_UP, khong phai HALF_EVEN")
    void percentDiscountRoundsHalfUp(long subtotal, long percent, long expected) {
        Coupon coupon = new Coupon()
                .setCode("PERCENT")
                .setType(CouponDomainService.TYPE_PERCENT)
                .setValue(percent);

        assertEquals(expected, orderDomainService.calcDiscount(coupon, subtotal));
    }

    /**
     * Bằng chứng dương cho ca ngay trên: HALF_EVEN <b>thật sự</b> cho kết quả khác.
     * <p>
     * Không có ca này thì khẳng định "đây là HALF_UP" chỉ là một con số khớp với một con số — nó
     * không chứng minh rằng phép đo phân biệt được hai quy ước. {@code 1050 * 5% = 52.5}: HALF_UP
     * ra 53, HALF_EVEN ra 52.
     */
    @Test
    @DisplayName("Control duong: 52.5 lam tron ra 53 chu KHONG phai 52 cua HALF_EVEN")
    void halfUpDiffersFromHalfEvenOnTheBoundary() {
        Coupon coupon = new Coupon()
                .setCode("PERCENT")
                .setType(CouponDomainService.TYPE_PERCENT)
                .setValue(5L);

        long discount = orderDomainService.calcDiscount(coupon, 1050L);

        assertEquals(53L, discount, "HALF_UP phai ra 53");
        assertNotEquals(52L, discount, "52 la ket qua cua HALF_EVEN — quy uoc bi doi");
    }

    @Test
    @DisplayName("calcDiscount cua ma fixed lay thang gia tri, khong nhan phan tram")
    void fixedDiscountTakesValueAsIs() {
        Coupon coupon = new Coupon()
                .setCode("HUUCO50")
                .setType(CouponDomainService.TYPE_FIXED)
                .setValue(50_000L);

        assertEquals(50_000L, orderDomainService.calcDiscount(coupon, 500_000L));
    }

    @Test
    @DisplayName("Khong ap ma -> discount 0")
    void noCouponMeansNoDiscount() {
        assertEquals(0L, orderDomainService.calcDiscount(null, 500_000L));
    }

    /**
     * Số tiền giảm không bao giờ vượt {@code subtotal} — một {@code total} âm là chứng từ nói rằng
     * cửa hàng nợ khách tiền.
     */
    @Test
    @DisplayName("Ma fixed lon hon gia tri don bi chan tren o dung subtotal")
    void fixedDiscountIsCappedAtSubtotal() {
        Coupon coupon = new Coupon()
                .setCode("QUA-LON")
                .setType(CouponDomainService.TYPE_FIXED)
                .setValue(900_000L);

        assertEquals(100_000L, orderDomainService.calcDiscount(coupon, 100_000L));
    }

    @Test
    @DisplayName("Coupon co type rong khong nem NPE, roi ve nhanh fixed")
    void nullTypeDoesNotThrow() {
        Coupon coupon = new Coupon().setCode("HONG").setValue(1_000L);

        assertEquals(1_000L, orderDomainService.calcDiscount(coupon, 100_000L));
    }

    // ========== SHIPPING: TINH TREN SO DA TRU GIAM GIA ==========

    /**
     * Ngưỡng miễn phí là {@code >=}, không phải {@code >} (§Contract 2).
     * <p>
     * Hai con số 499.999 và 500.000 là <b>cặp ca ranh giới</b> mà §Evidence của ticket đòi đo bằng
     * request thật; ở đây chúng khoá luôn phép tính thuần, nên một lần đổi dấu so sánh sẽ đỏ ngay
     * trong lane test mặc định chứ không phải chờ tới lúc gõ curl.
     *
     * @param amountAfterDiscount hiệu subtotal trừ giảm giá
     * @param expected phí vận chuyển mong đợi
     */
    @ParameterizedTest(name = "subtotal-discount={0} -> shippingFee={1}")
    @CsvSource({
            "0,      30000",
            "499999, 30000",
            "500000, 0",
            "500001, 0"
    })
    @DisplayName("Nguong mien phi van chuyen la >=500000, khong phai >500000")
    void shippingFeeUsesInclusiveThreshold(long amountAfterDiscount, long expected) {
        assertEquals(expected, orderDomainService.calcShippingFee(amountAfterDiscount));
    }

    /**
     * <b>Ca đáng giá nhất của cả file.</b> Một mã giảm giá kéo đơn từ trên ngưỡng xuống dưới ngưỡng
     * thì phí vận chuyển <i>phải</i> xuất hiện trở lại.
     * <p>
     * Nếu ai đó tính phí trên {@code subtotal} trần thì đơn này vẫn miễn phí ship, hệ thống vẫn
     * chạy, mọi test khác vẫn xanh — và con số cuối lệch đúng 30.000 ₫ so với thứ
     * {@code orders.api.ts:createOrder} vừa hiển thị cho khách trước khi bấm nút.
     */
    @Test
    @DisplayName("Giam gia keo don xuong duoi nguong thi phi ship quay lai — KHONG tinh tren subtotal tran")
    void discountCanPushOrderBackBelowFreeShippingThreshold() {
        Coupon coupon = new Coupon()
                .setCode("HUUCO50")
                .setType(CouponDomainService.TYPE_FIXED)
                .setValue(50_000L);
        long subtotal = 520_000L;

        long discount = orderDomainService.calcDiscount(coupon, subtotal);
        long shippingFee = orderDomainService.calcShippingFee(subtotal - discount);

        assertEquals(50_000L, discount);
        assertEquals(30_000L, shippingFee, "470.000 duoi nguong — tinh tren 520.000 tran se ra 0");
        assertEquals(500_000L, orderDomainService.calcTotal(subtotal, discount, shippingFee));
    }

    @Test
    @DisplayName("calcTotal = subtotal - discount + shippingFee")
    void totalFollowsTheFixedFormula() {
        assertEquals(1_000_000L, orderDomainService.calcTotal(1_000_000L, 30_000L, 30_000L));
    }

    // ========== MA DON ==========

    /**
     * Khuôn mã đơn là {@code NSS-YYYYMMDD-NNNN}, số thứ tự <b>đếm trên toàn bộ bảng</b> và
     * <b>đệm 0 tới bốn chữ số</b> (§Contract 6).
     */
    @Test
    @DisplayName("genOrderCode dung khuon NSS-YYYYMMDD-NNNN va dem 0 toi bon chu so")
    void orderCodeFollowsContractShape() {
        when(orderRepository.countOrders()).thenReturn(0L);

        String code = orderDomainService.genOrderCode(LocalDateTime.of(2026, 8, 17, 10, 30));

        assertEquals("NSS-20260817-0001", code);
    }

    @Test
    @DisplayName("So thu tu la tong so don + 1, khong phai so don trong ngay")
    void orderSequenceCountsWholeTable() {
        when(orderRepository.countOrders()).thenReturn(41L);

        assertEquals("NSS-20260817-0042",
                orderDomainService.genOrderCode(LocalDateTime.of(2026, 8, 17, 10, 30)));
    }

    /**
     * Số thứ tự vượt 9999 thì mã <b>dài ra</b> chứ không bị cắt — thà mã dài còn hơn hai đơn mang
     * cùng một mã, vì cột {@code code} có ràng buộc duy nhất {@code uk_code}.
     */
    @Test
    @DisplayName("So thu tu vuot 9999 lam ma dai ra, KHONG bi cat")
    void orderSequenceIsNotTruncatedBeyondFourDigits() {
        when(orderRepository.countOrders()).thenReturn(9_999L);

        assertEquals("NSS-20260817-10000",
                orderDomainService.genOrderCode(LocalDateTime.of(2026, 8, 17, 10, 30)));
    }

    // ========== GHI: CONDITIONAL UPDATE ==========

    /**
     * Trừ kho uỷ thác thẳng cho conditional UPDATE của adapter, và trả về đúng phán quyết của nó.
     * <p>
     * Kiểm bằng {@code ArgumentCaptor} chứ không chỉ kiểm kết quả: điều cần chứng minh là số lượng
     * <i>đi xuống nguyên vẹn</i>, vì một phép biến đổi lặng lẽ ở đây (chia, cộng, lấy trị tuyệt đối)
     * sẽ trừ sai kho mà vẫn trả về {@code true}.
     */
    @Test
    @DisplayName("deductStock chuyen thang so luong xuong conditional UPDATE")
    void deductStockDelegatesQuantityUnchanged() {
        when(productRepository.decreaseStock(anyLong(), anyInt())).thenReturn(true);

        assertTrue(orderDomainService.deductStock(7L, 3));

        ArgumentCaptor<Integer> quantity = ArgumentCaptor.forClass(Integer.class);
        verify(productRepository).decreaseStock(anyLong(), quantity.capture());
        assertEquals(3, quantity.getValue());
    }

    @Test
    @DisplayName("deductStock tra false khi adapter noi khong du hang")
    void deductStockReportsFailure() {
        when(productRepository.decreaseStock(anyLong(), anyInt())).thenReturn(false);

        assertFalse(orderDomainService.deductStock(7L, 3));
    }

    /**
     * Số lượng không dương <b>không được chạm tới database</b>.
     * <p>
     * Một {@code UPDATE ... SET stock = stock - 0} luôn thành công và luôn trả về 1 dòng ảnh hưởng,
     * nên nó sẽ báo "trừ kho xong" cho một dòng hàng chẳng mua gì. Chặn ở đây rẻ hơn là dựa vào
     * validate ở tầng DTO, vốn không bao trùm mọi lời gọi tới domain.
     */
    @Test
    @DisplayName("deductStock voi so luong khong duong khong goi xuong repository")
    void deductStockRejectsNonPositiveQuantity() {
        assertFalse(orderDomainService.deductStock(7L, 0));
        assertFalse(orderDomainService.deductStock(7L, -1));

        verify(productRepository, never()).decreaseStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("redeemCoupon uy thac cho conditional UPDATE va tra dung phan quyet cua no")
    void redeemCouponDelegatesToConditionalUpdate() {
        when(couponRepository.increaseUsedCount(anyString())).thenReturn(true);
        assertTrue(orderDomainService.redeemCoupon("HUUCO50"));

        when(couponRepository.increaseUsedCount(anyString())).thenReturn(false);
        assertFalse(orderDomainService.redeemCoupon("HUUCO50"));
    }

    @Test
    @DisplayName("redeemCoupon voi ma rong khong goi xuong repository")
    void redeemCouponIgnoresBlankCode() {
        assertFalse(orderDomainService.redeemCoupon(null));
        assertFalse(orderDomainService.redeemCoupon("   "));

        verify(couponRepository, never()).increaseUsedCount(anyString());
    }

    /**
     * Dòng nhật ký đầu tiên có {@code fromStatus} là {@code null} và {@code toStatus} là 0
     * (§Contract 9 bước 5).
     * <p>
     * {@code fromStatus = null} không phải chỗ quên: đơn không đi <i>từ</i> đâu cả, và ghi 0 vào đó
     * sẽ nói rằng đơn đã ở {@code pending} trước khi nó tồn tại.
     */
    @Test
    @DisplayName("Dong nhat ky dau tien: fromStatus null, toStatus 0, changedBy di nguyen ven")
    void firstHistoryRowHasNullFromStatus() {
        when(orderRepository.saveHistory(any())).thenAnswer(call -> call.getArgument(0));
        Order order = new Order().setId(1L).setCode("NSS-20260817-0001");
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 17, 10, 30);

        OrderStatusHistory history = orderDomainService.recordCreation(order, "guest", createdAt);

        assertNull(history.getFromStatus(), "Dong dau tien khong di TU trang thai nao");
        assertEquals(OrderDomainService.STATUS_PENDING, history.getToStatus());
        assertEquals("guest", history.getChangedBy());
        assertEquals(createdAt, history.getCreatedAt());
        assertEquals(order, history.getOrder());
    }

    // ========== DOC ==========

    /**
     * {@code findByUserId(null)} trả danh sách rỗng và <b>không hỏi database</b>.
     * <p>
     * Đây là hàng rào cuối của §C.4.1 ở tầng domain: một {@code userId} rỗng tuyệt đối không được
     * biến thành "trả về tất cả". Ca này khoá lại điều đó ở chỗ mà một lần sửa vội dễ nới nhất.
     */
    @Test
    @DisplayName("findByUserId(null) tra rong va KHONG hoi database — khong bao gio la 'tat ca'")
    void findByUserIdRejectsNullOwner() {
        assertTrue(orderDomainService.findByUserId(null).isEmpty());

        verify(orderRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("findByCode voi ma rong tra null va khong hoi database")
    void findByCodeIgnoresBlankCode() {
        assertNull(orderDomainService.findByCode(null));
        assertNull(orderDomainService.findByCode("   "));

        verify(orderRepository, never()).findByCode(anyString());
    }

    /**
     * Mã đơn được cắt khoảng trắng hai đầu trước khi xuống repository.
     * <p>
     * Kiểm bằng {@code ArgumentCaptor} chứ không kiểm kết quả, cùng khuôn với
     * {@code CouponDomainServiceTest}: mock trả cùng một thứ cho mọi đầu vào, nên chỉ chuỗi
     * <i>thật sự đi xuống</i> mới chứng minh được phép cắt đã xảy ra.
     */
    @Test
    @DisplayName("findByCode cat khoang trang truoc khi xuong repository")
    void findByCodeTrimsBeforeQuery() {
        orderDomainService.findByCode("  NSS-20260817-0001  ");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(orderRepository).findByCode(code.capture());
        assertEquals("NSS-20260817-0001", code.getValue());
    }

    @Test
    @DisplayName("findItemsGroupedByOrderId voi danh sach rong khong hoi database")
    void groupedItemsOfEmptyListSkipsQuery() {
        assertTrue(orderDomainService.findItemsGroupedByOrderId(List.of()).isEmpty());
        assertTrue(orderDomainService.findItemsGroupedByOrderId(null).isEmpty());

        verify(orderRepository, never()).findItemsByOrderIds(any());
    }

    @Test
    @DisplayName("findOwnerById(null) la don khach vang lai — tra null, khong hoi database")
    void guestOrderHasNoOwnerLookup() {
        assertNull(orderDomainService.findOwnerById(null));

        verify(userRepository, never()).findById(anyLong());
    }
}
