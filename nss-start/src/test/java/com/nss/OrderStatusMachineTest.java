package com.nss;

import com.nss.ddd.domain.repository.CouponRepository;
import com.nss.ddd.domain.repository.OrderRepository;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.repository.UserRepository;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.domain.service.impl.OrderDomainServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Kiểm <b>máy trạng thái đơn hàng</b> — {@code OrderDomainService.canTransition}, luật của
 * API_CONTRACT §B.12.2.
 * <p>
 * <b>Cách kiểm ở đây là quét TOÀN BỘ 25 ô của ma trận 5×5, không phải liệt kê vài ca tiêu biểu</b>
 * — và đó là điểm cốt lõi của file này. Một máy trạng thái sai thường không sai ở ô người ta nghĩ
 * tới: nó sai ở ô người ta <i>không</i> nghĩ tới. Sáu ô hợp lệ được liệt kê tường minh, mười chín ô
 * còn lại phải trả {@code false}, và cả hai nửa đều được khẳng định.
 * <p>
 * <b>Ba ô đáng chú ý nằm trong mười chín ô kia, và hợp đồng nói thẳng về cả ba:</b>
 * <ul>
 *   <li><b>Năm ô trên đường chéo</b> — chuyển sang <i>chính</i> trạng thái hiện tại. §B.12.2:
 *       "Kể cả {@code status} trùng trạng thái hiện tại cũng là 422: nó không nằm trong danh sách
 *       được phép." Đây là ca dễ "sửa cho tiện" nhất của cả endpoint.</li>
 *   <li><b>Tám ô xuất phát từ {@code delivered} / {@code cancelled}</b> — hai trạng thái cuối. Bảng
 *       chuyển của chúng <i>rỗng</i>, tức "không quay lui được", không phải "chưa liệt kê".</li>
 *   <li><b>Mọi ô đi ngược dòng</b> ({@code shipping} {@literal ->} {@code pending} chẳng hạn).</li>
 * </ul>
 * <b>Con số trạng thái lấy từ hằng của {@link OrderDomainService}, không viết lại {@code 0..4}</b> —
 * chép con số vào test là làm test đổi theo cùng lúc với code và không bắt được gì.
 */
class OrderStatusMachineTest {

    /** Năm trạng thái hợp lệ, theo đúng thứ tự vòng đời của một đơn. */
    private static final int[] ALL_STATUSES = {
            OrderDomainService.STATUS_PENDING,
            OrderDomainService.STATUS_CONFIRMED,
            OrderDomainService.STATUS_SHIPPING,
            OrderDomainService.STATUS_DELIVERED,
            OrderDomainService.STATUS_CANCELLED
    };

    private final OrderDomainService orderDomainService = new OrderDomainServiceImpl(
            mock(OrderRepository.class),
            mock(ProductRepository.class),
            mock(CouponRepository.class),
            mock(UserRepository.class));

    // ========== SAU O HOP LE ==========

    /**
     * @param from trạng thái hiện tại
     * @param to trạng thái muốn chuyển sang
     */
    @ParameterizedTest(name = "{0} -> {1} hop le")
    @CsvSource({
            "0, 1",
            "0, 4",
            "1, 2",
            "1, 4",
            "2, 3",
            "2, 4"
    })
    @DisplayName("Dung SAU nuoc di hop le cua bang §B.12.2")
    void allowsExactlySixTransitions(int from, int to) {
        assertTrue(orderDomainService.canTransition(from, to),
                "Nuoc di " + from + " -> " + to + " phai hop le theo §B.12.2");
    }

    /**
     * <b>Ma trận 5×5: sáu ô hợp lệ, mười chín ô còn lại phải là {@code false}.</b>
     * <p>
     * Đây là nửa <i>quan trọng hơn</i> của phép kiểm. Ca ở trên vẫn xanh với một cài đặt trả
     * {@code true} cho mọi thứ; chỉ ca này bắt được điều đó. Nó cũng là chỗ duy nhất khẳng định năm
     * ô đường chéo và tám ô xuất phát từ hai trạng thái cuối.
     */
    @Test
    @DisplayName("Muoi chin o con lai cua ma tran 5x5 deu bi tu choi")
    void rejectsEveryOtherCell() {
        int allowed = 0;
        int rejected = 0;
        for (int from : ALL_STATUSES) {
            for (int to : ALL_STATUSES) {
                if (orderDomainService.canTransition(from, to)) {
                    allowed++;
                } else {
                    rejected++;
                }
            }
        }
        assertEquals(6, allowed, "Bang §B.12.2 co dung sau nuoc di hop le");
        assertEquals(19, rejected, "Muoi chin o con lai phai bi tu choi");
    }

    /**
     * Chuyển sang <b>chính</b> trạng thái hiện tại là <b>không hợp lệ</b> — §B.12.2 nói thẳng.
     * <p>
     * Tách khỏi ca ma trận ở trên dù nó đã phủ: một ngày nào đó ai đó sẽ "sửa cho tiện" đúng năm ô
     * này, và một ca đỏ mang đúng tên gọi của luật thì đọc ra ngay vì sao nó đỏ.
     *
     * @param status trạng thái được dùng cho cả hai đầu
     */
    @ParameterizedTest(name = "{0} -> {0} bi tu choi")
    @ValueSource(ints = {0, 1, 2, 3, 4})
    @DisplayName("Chuyen sang CHINH trang thai hien tai la 422, khong phai no-op")
    void rejectsTransitionToSameStatus(int status) {
        assertFalse(orderDomainService.canTransition(status, status),
                "Chuyen sang chinh trang thai hien tai khong nam trong danh sach duoc phep");
    }

    /**
     * {@code delivered} và {@code cancelled} không quay lui được — bảng chuyển của chúng
     * <b>rỗng</b>, không phải "chưa liệt kê".
     *
     * @param terminal trạng thái cuối
     */
    @ParameterizedTest(name = "{0} khong con nuoc di nao")
    @ValueSource(ints = {3, 4})
    @DisplayName("delivered va cancelled la trang thai CUOI — khong nuoc di nao")
    void terminalStatusesHaveNoOutgoingTransitions(int terminal) {
        for (int to : ALL_STATUSES) {
            assertFalse(orderDomainService.canTransition(terminal, to),
                    "Trang thai cuoi " + terminal + " khong duoc chuyen sang " + to);
        }
    }

    /**
     * Con số ngoài dải {@code 0..4} luôn cho {@code false} — <b>không ném exception</b>.
     * <p>
     * Một {@code status} lạ trong DB (dữ liệu cũ, hoặc ai đó {@code UPDATE} tay) phải dừng ở đây
     * bằng một câu trả lời, không phải bằng một {@code NullPointerException} giữa đường đổi trạng
     * thái.
     *
     * @param outOfRange con số ngoài dải
     */
    @ParameterizedTest(name = "status={0} ngoai dai -> false")
    @ValueSource(ints = {-1, 5, 99, Integer.MIN_VALUE, Integer.MAX_VALUE})
    @DisplayName("Con so ngoai dai 0..4 cho false, khong nem exception")
    void outOfRangeStatusIsRejectedQuietly(int outOfRange) {
        assertFalse(orderDomainService.canTransition(outOfRange, OrderDomainService.STATUS_CONFIRMED));
        assertFalse(orderDomainService.canTransition(OrderDomainService.STATUS_PENDING, outOfRange));
    }

    // ========== KHUNG NGAY THEO GIO CUA HANG ==========

    /**
     * Khung ngày có <b>đúng {@code days}</b> phần tử, tăng dần, và <b>kết thúc ở hôm nay theo giờ
     * cửa hàng</b>.
     * <p>
     * <b>Ba khẳng định, và cái thứ ba là cái dễ sai:</b> {@code days=7} phải là "hôm nay và sáu
     * ngày trước", không phải "bảy ngày trước hôm nay" — khớp {@code buildDateWindow} của frontend.
     * Lệch một ngày ở đây làm cả bốn con số phụ thuộc {@code days} nói về một khoảng khác.
     * <p>
     * "Hôm nay" đọc theo {@link OrderDomainService#STORE_ZONE}, không theo giờ máy: lúc 01:00 giờ
     * Việt Nam thì UTC vẫn còn là hôm qua.
     *
     * @param days số ngày yêu cầu
     */
    @ParameterizedTest(name = "days={0} cho dung {0} ngay, ket thuc hom nay")
    @ValueSource(ints = {1, 7, 30, 365})
    @DisplayName("genDateWindow: dung `days` ngay lien tiep, tang dan, ket thuc hom nay")
    void dateWindowHasExactlyDaysEndingToday(int days) {
        List<LocalDate> window = orderDomainService.genDateWindow(days);

        assertEquals(days, window.size(), "Khung ngay phai co dung `days` phan tu");
        LocalDate today = LocalDate.now(OrderDomainService.STORE_ZONE);
        assertEquals(today, window.get(window.size() - 1), "Phan tu cuoi phai la hom nay (gio cua hang)");
        assertEquals(today.minusDays(days - 1L), window.get(0),
                "days=N nghia la hom nay va N-1 ngay truoc, khong phai N ngay TRUOC hom nay");
        for (int index = 1; index < window.size(); index++) {
            assertEquals(window.get(index - 1).plusDays(1), window.get(index),
                    "Khung ngay phai lien tiep va tang dan");
        }
    }
}
