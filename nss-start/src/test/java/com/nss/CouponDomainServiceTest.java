package com.nss;

import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.repository.CouponRepository;
import com.nss.ddd.domain.service.CouponDomainService;
import com.nss.ddd.domain.service.impl.CouponDomainServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm {@code CouponDomainServiceImpl} — quy tắc thuần, repository là mock, không cần database.
 * <p>
 * <b>Phase 3 sẽ gọi lại chính hai vị từ được kiểm ở đây</b> khi tạo đơn, nên file này không chỉ
 * bảo vệ {@code POST /coupons/validate}: nó khoá định nghĩa "mã này dùng được không" cho cả luồng
 * đặt hàng. Một mã bị nới lỏng ở đây sẽ thành một đơn được giảm giá bằng mã mà endpoint xác thực
 * vừa từ chối.
 * <p>
 * Ca đáng giá nhất là {@link #redeemabilityComparesInUtcNotLocalTime()}: nó khoá quy ước giờ UTC
 * bằng một mốc chỉ sai khi ai đó đổi sang {@code LocalDateTime.now()} trần — máy dev ở
 * {@code Asia/Saigon} là UTC+7 nên lệch đó <i>không ném lỗi</i>, chỉ khiến mã hết hạn vẫn dùng được
 * thêm 7 tiếng.
 */
class CouponDomainServiceTest {

    private final CouponRepository couponRepository = mock(CouponRepository.class);

    private final CouponDomainService couponDomainService = new CouponDomainServiceImpl(couponRepository);

    // ========== TRA MA: TRIM + BO QUA HOA THUONG ==========

    /**
     * Chuỗi người dùng gõ được cắt khoảng trắng <b>trước khi</b> xuống repository.
     * <p>
     * Kiểm bằng {@code ArgumentCaptor} chứ không kiểm kết quả: mock trả cùng một mã cho mọi đầu
     * vào, nên chỉ có chuỗi <i>thật sự đi xuống</i> mới chứng minh được phép cắt đã xảy ra. Việc bỏ
     * qua hoa thường nằm trong câu JPQL ({@code UPPER(c.code) = UPPER(:code)}) nên không thuộc phạm
     * vi file này — nó được kiểm bằng request thật.
     *
     * @param rawCode chuỗi người dùng gõ với đủ kiểu khoảng trắng thừa
     */
    @ParameterizedTest(name = "findByCode({0}) cat khoang trang truoc khi xuong repository")
    @ValueSource(strings = {"  huuco50  ", "huuco50  ", "  huuco50", "\thuuco50\n"})
    @DisplayName("findByCode cat khoang trang hai dau truoc khi tra")
    void findByCodeTrimsBeforeLookup(String rawCode) {
        when(couponRepository.findByCode(anyString())).thenReturn(Optional.of(genActiveCoupon()));

        couponDomainService.findByCode(rawCode);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(couponRepository).findByCode(captor.capture());
        assertEquals("huuco50", captor.getValue());
    }

    /**
     * Mã trả về là bản ghi <b>nguyên trạng trong DB</b>, không phải chuỗi người dùng gõ.
     * <p>
     * Đây là điều khoản §Contract 4 dễ vi phạm nhất một cách vô tình: dội lại chuỗi đầu vào cho
     * "tiện" sẽ khiến response mang {@code "  huuco50  "} và frontend hiển thị đúng chuỗi đó.
     */
    @Test
    @DisplayName("findByCode tra ma CHUAN trong DB, khong doi lai chuoi nguoi dung go")
    void findByCodeReturnsCanonicalCode() {
        when(couponRepository.findByCode(anyString())).thenReturn(Optional.of(genActiveCoupon()));

        Coupon found = couponDomainService.findByCode("  huuco50  ");

        assertEquals("HUUCO50", found.getCode());
    }

    /**
     * Chuỗi rỗng / chỉ có khoảng trắng dừng lại <b>trước</b> repository.
     * <p>
     * {@code verify(never())} là phần có giá trị: nó chứng minh không có truy vấn nào được bắn ra,
     * chứ không chỉ chứng minh kết quả là {@code null}.
     *
     * @param rawCode chuỗi không mang mã nào
     */
    @ParameterizedTest(name = "findByCode({0}) khong cham toi repository")
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("Chuoi rong hoac toan khoang trang khong bay truy van nao")
    void blankCodeShortCircuits(String rawCode) {
        assertNull(couponDomainService.findByCode(rawCode));
        verify(couponRepository, never()).findByCode(anyString());
    }

    @Test
    @DisplayName("findByCode(null) tra null va khong bay truy van nao")
    void nullCodeShortCircuits() {
        assertNull(couponDomainService.findByCode(null));
        verify(couponRepository, never()).findByCode(anyString());
    }

    // ========== QUY TAC HIEU LUC ==========

    /**
     * Ca thuận — <b>positive control</b> cho cả nhóm ca từ chối bên dưới.
     * <p>
     * Không có ca này thì một {@code isRedeemable} trả {@code false} vô điều kiện sẽ làm mọi ca từ
     * chối xanh hết, và phép kiểm không nói được gì cả.
     */
    @Test
    @DisplayName("CONTROL: ma seed (bat, khong gioi han thoi gian, khong gioi han luot) dung duoc")
    void seedShapedCouponIsRedeemable() {
        assertTrue(couponDomainService.isRedeemable(genActiveCoupon()));
    }

    @Test
    @DisplayName("isActive = false thi khong dung duoc")
    void inactiveCouponRejected() {
        assertFalse(couponDomainService.isRedeemable(genActiveCoupon().setIsActive(false)));
    }

    @Test
    @DisplayName("Chua toi startsAt thi khong dung duoc")
    void couponBeforeWindowRejected() {
        Coupon coupon = genActiveCoupon().setStartsAt(genNowUtc().plusDays(1));
        assertFalse(couponDomainService.isRedeemable(coupon));
    }

    @Test
    @DisplayName("Da qua endsAt thi khong dung duoc")
    void couponAfterWindowRejected() {
        Coupon coupon = genActiveCoupon().setEndsAt(genNowUtc().minusDays(1));
        assertFalse(couponDomainService.isRedeemable(coupon));
    }

    @Test
    @DisplayName("Trong cua so startsAt-endsAt thi dung duoc")
    void couponInsideWindowAccepted() {
        Coupon coupon = genActiveCoupon()
                .setStartsAt(genNowUtc().minusDays(1))
                .setEndsAt(genNowUtc().plusDays(1));
        assertTrue(couponDomainService.isRedeemable(coupon));
    }

    /**
     * Quy ước giờ <b>UTC</b>, khoá bằng một mốc chỉ sai khi ai đó bỏ {@code ZoneOffset.UTC}.
     * <p>
     * Mã hết hạn cách đây 3 tiếng theo giờ UTC. Máy dev ở {@code Asia/Saigon} (UTC+7) chạy
     * {@code LocalDateTime.now()} trần sẽ cho ra một mốc <i>lớn hơn</i> mốc UTC 7 tiếng — vẫn là
     * "sau {@code endsAt}", nên ca này vẫn xanh. Chiều ngược lại mới bắt được: mã <i>bắt đầu</i>
     * cách đây 3 tiếng UTC nhưng theo giờ địa phương thì {@code now} lệch 7 tiếng, đủ để một mã
     * chưa tới hạn bị coi là đã tới. Hai khẳng định dưới đây kẹp cả hai đầu cửa sổ ở khoảng cách
     * nhỏ hơn 7 tiếng, nên bất kỳ lệch múi giờ nào cũng làm một trong hai đỏ.
     */
    @Test
    @DisplayName("Cua so hieu luc so theo gio UTC, khong theo gio may (lech 7 tieng o VN)")
    void redeemabilityComparesInUtcNotLocalTime() {
        LocalDateTime nowUtc = genNowUtc();

        // Ma vua bat dau 3 tieng truoc va con 3 tieng nua moi het: cua so rong 6 tieng < 7 tieng lech
        Coupon inside = genActiveCoupon()
                .setStartsAt(nowUtc.minusHours(3))
                .setEndsAt(nowUtc.plusHours(3));
        assertTrue(couponDomainService.isRedeemable(inside),
                "mot LocalDateTime.now() tran o UTC+7 se day now ra ngoai cua so 6 tieng nay");

        // Ma bat dau sau 3 tieng nua: gio may o UTC+7 se tuong da toi han
        Coupon notYetStarted = genActiveCoupon().setStartsAt(nowUtc.plusHours(3));
        assertFalse(couponDomainService.isRedeemable(notYetStarted),
                "mot LocalDateTime.now() tran o UTC+7 se coi ma chua bat dau nay la da bat dau");
    }

    @Test
    @DisplayName("usedCount >= usageLimit thi khong dung duoc")
    void exhaustedCouponRejected() {
        assertFalse(couponDomainService.isRedeemable(
                genActiveCoupon().setUsageLimit(5).setUsedCount(5)));
        assertFalse(couponDomainService.isRedeemable(
                genActiveCoupon().setUsageLimit(0).setUsedCount(0)));
    }

    @Test
    @DisplayName("usageLimit NULL nghia la khong gioi han luot")
    void nullUsageLimitMeansUnlimited() {
        assertTrue(couponDomainService.isRedeemable(
                genActiveCoupon().setUsageLimit(null).setUsedCount(9999)));
    }

    @Test
    @DisplayName("Con luot thi dung duoc")
    void couponWithRemainingUsesAccepted() {
        assertTrue(couponDomainService.isRedeemable(
                genActiveCoupon().setUsageLimit(5).setUsedCount(4)));
    }

    @Test
    @DisplayName("isRedeemable(null) tra false thay vi nem NullPointerException")
    void nullCouponIsNotRedeemable() {
        assertFalse(couponDomainService.isRedeemable(null));
    }

    // ========== NGUONG GIA TRI DON ==========

    /**
     * Ngưỡng là {@code >=}, không phải {@code >}.
     * <p>
     * Đúng con số {@code minOrderValue} phải <b>qua</b>. Lệch một đơn vị ở đây là loại lỗi không ai
     * nhìn thấy cho tới khi có một khách đặt đơn đúng bằng ngưỡng và bị từ chối.
     */
    @Test
    @DisplayName("subtotal bang dung minOrderValue thi DAT — nguong la >=, khong phai >")
    void thresholdIsInclusive() {
        Coupon coupon = genActiveCoupon().setMinOrderValue(500000L);

        assertFalse(couponDomainService.meetsMinOrderValue(coupon, 499999L));
        assertTrue(couponDomainService.meetsMinOrderValue(coupon, 500000L));
        assertTrue(couponDomainService.meetsMinOrderValue(coupon, 500001L));
    }

    @Test
    @DisplayName("subtotal null coi nhu 0, khong nem NullPointerException")
    void nullSubtotalTreatedAsZero() {
        assertFalse(couponDomainService.meetsMinOrderValue(genActiveCoupon().setMinOrderValue(1L), null));
        assertTrue(couponDomainService.meetsMinOrderValue(genActiveCoupon().setMinOrderValue(0L), null));
    }

    @Test
    @DisplayName("meetsMinOrderValue(null, ...) tra false thay vi nem NullPointerException")
    void nullCouponDoesNotMeetThreshold() {
        assertFalse(couponDomainService.meetsMinOrderValue(null, 999999L));
    }

    // ========== DANH SACH MA DANG CHAY ==========

    /**
     * {@code findRedeemableCoupons} truyền mốc <b>giờ UTC</b> xuống repository.
     * <p>
     * Việc lọc nằm ở tầng SQL nên phép kiểm ở đây chỉ trả lời được đúng một câu hỏi — và đó là câu
     * hỏi dễ sai nhất: mốc thời gian đi xuống có phải giờ UTC không. Biên độ 1 phút đủ chặt để bắt
     * lệch 7 tiếng và đủ rộng để không đỏ vì thời gian chạy test.
     */
    @Test
    @DisplayName("findRedeemableCoupons truyen moc gio UTC xuong repository")
    void activeCouponsQueryUsesUtcNow() {
        when(couponRepository.findRedeemable(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(genActiveCoupon()));

        List<Coupon> result = couponDomainService.findRedeemableCoupons();

        assertEquals(1, result.size());
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(couponRepository).findRedeemable(captor.capture());
        LocalDateTime passed = captor.getValue();
        long driftSeconds = Math.abs(java.time.Duration.between(genNowUtc(), passed).getSeconds());
        assertTrue(driftSeconds < 60,
                "moc truyen xuong lech " + driftSeconds + "s so voi gio UTC — nghi ngo LocalDateTime.now() tran");
    }

    // ========== FIXTURES ==========

    /**
     * @return mã có hình dạng đúng như ba mã seed của ticket 0006: bật, không giới hạn thời gian,
     *         không giới hạn lượt
     */
    private Coupon genActiveCoupon() {
        return new Coupon()
                .setCode("HUUCO50")
                .setType(1)
                .setValue(50000L)
                .setMinOrderValue(500000L)
                .setDescription("Giảm ngay 50.000 ₫ cho đơn từ 500.000 ₫.")
                .setIsActive(true)
                .setStartsAt(null)
                .setEndsAt(null)
                .setUsageLimit(null)
                .setUsedCount(0);
    }

    /**
     * @return thời điểm hiện tại theo giờ UTC — cùng quy ước với chỗ đang được kiểm
     */
    private LocalDateTime genNowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
