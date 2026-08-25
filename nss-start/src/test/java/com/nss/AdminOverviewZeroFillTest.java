package com.nss;

import com.nss.ddd.application.mapper.OrderMapper;
import com.nss.ddd.application.model.response.AdminOverviewResponse;
import com.nss.ddd.application.service.stats.StatsAppService;
import com.nss.ddd.application.service.stats.impl.StatsAppServiceImpl;
import com.nss.ddd.domain.model.DailyRevenue;
import com.nss.ddd.domain.model.StatusCount;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.domain.service.ProductDomainService;
import com.nss.ddd.domain.service.UserDomainService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kiểm <b>zero-fill và hai bất biến</b> của {@code GET /admin/stats/overview} (§B.12.4) —
 * {@code StatsAppServiceImpl} với domain service là mock, không cần database.
 * <p>
 * <b>Đây là phép kiểm bắt được đúng thứ một request thật KHÔNG bắt được.</b> Trên dữ liệu thật,
 * mọi ngày đều có thể có đơn và mọi trạng thái đều có thể khác 0 — một response toàn số khác 0
 * không chứng minh được zero-fill có tồn tại. Ở đây truy vấn trả về <b>một dòng duy nhất</b>, nên
 * 29/30 phần tử của {@code revenueByDay} và 4/5 phần tử của {@code ordersByStatus} <i>chỉ có thể</i>
 * đến từ bước zero-fill.
 * <p>
 * <b>Hai bất biến được kiểm bằng cách tự cộng lại từ chính payload</b>, đúng cách một client sẽ
 * kiểm:
 * <ul>
 *   <li>{@code revenue == sum(revenueByDay[].revenue)};</li>
 *   <li>{@code orderCount == sum(ordersByStatus[].count)}.</li>
 * </ul>
 * Chúng đúng <i>theo cấu tạo</i> — cả hai tổng được cộng từ chính mảng đã zero-fill — nên ca này
 * cũng là lưới chặn cho việc ai đó "tối ưu" bằng cách thêm một truy vấn tổng thứ hai.
 */
class AdminOverviewZeroFillTest {

    /** Ngày duy nhất có đơn trong mọi ca dưới đây — cố ý KHÔNG phải hôm nay và cũng không phải ngày đầu. */
    private static final long REVENUE_OF_THE_ONLY_DAY = 798_000L;

    private final OrderDomainService orderDomainService = mock(OrderDomainService.class);

    private final UserDomainService userDomainService = mock(UserDomainService.class);

    private final ProductDomainService productDomainService = mock(ProductDomainService.class);

    private final StatsAppService statsAppService = new StatsAppServiceImpl(
            orderDomainService, userDomainService, productDomainService);

    /**
     * <b>{@code revenueByDay} có đúng {@code days} phần tử dù truy vấn chỉ trả về MỘT dòng.</b>
     * <p>
     * Ba khẳng định: đủ số phần tử, tăng dần theo ngày, và <b>ít nhất một phần tử có
     * {@code revenue == 0}</b>. Khẳng định thứ ba là khẳng định thật: không có nó, một cài đặt
     * "trả nguyên kết quả truy vấn" vẫn xanh khi dữ liệu tình cờ dày.
     *
     * @param days số ngày của khoảng
     */
    @ParameterizedTest(name = "days={0} cho dung {0} phan tu, co it nhat mot revenue=0")
    @ValueSource(ints = {1, 7, 30, 365})
    @DisplayName("revenueByDay zero-fill du `days` phan tu, tang dan, co moc rong THAT")
    void revenueByDayIsZeroFilledToExactLength(int days) {
        List<LocalDate> window = genWindow(days);
        LocalDate dayWithOrders = window.get(window.size() - 1);
        stubDomain(window,
                List.of(DailyRevenue.of(dayWithOrders, REVENUE_OF_THE_ONLY_DAY)),
                List.of(StatusCount.of(OrderDomainService.STATUS_PENDING, 3L)));

        AdminOverviewResponse overview = statsAppService.findOverview(days);
        List<AdminOverviewResponse.DailyRevenueResponse> points = overview.getRevenueByDay();

        assertEquals(days, points.size(), "Phai co dung `days` phan tu, khong phai so ngay co don");
        for (int index = 1; index < points.size(); index++) {
            assertTrue(points.get(index - 1).getDate().compareTo(points.get(index).getDate()) < 0,
                    "revenueByDay phai tang dan theo ngay");
        }
        assertEquals(dayWithOrders.toString(), points.get(points.size() - 1).getDate(),
                "Phan tu cuoi la hom nay, dang YYYY-MM-DD");
        if (days > 1) {
            assertTrue(points.stream().anyMatch(point -> point.getRevenue() == 0L),
                    "Phai co it nhat mot moc revenue=0 — do la bang chung zero-fill co that");
        }
    }

    /**
     * <b>{@code ordersByStatus} có đủ CẢ NĂM trạng thái dù truy vấn chỉ trả về một dòng</b>, và bốn
     * phần tử còn lại mang {@code count: 0}.
     * <p>
     * Thứ tự cũng được khoá: nó phải là thứ tự vòng đời của một đơn, khớp {@code ORDER_STATUSES}
     * của frontend. Không khoá thì cột trạng thái nhảy chỗ mỗi lần tải lại.
     */
    @Test
    @DisplayName("ordersByStatus co du ca 5 trang thai, dung thu tu vong doi, co count=0 THAT")
    void ordersByStatusCoversAllFiveStatuses() {
        List<LocalDate> window = genWindow(30);
        stubDomain(window,
                List.of(DailyRevenue.of(window.get(29), REVENUE_OF_THE_ONLY_DAY)),
                List.of(StatusCount.of(OrderDomainService.STATUS_CANCELLED, 2L)));

        List<AdminOverviewResponse.StatusCountResponse> columns =
                statsAppService.findOverview(30).getOrdersByStatus();

        assertEquals(5, columns.size(), "Phai du ca 5 trang thai, ke ca trang thai dang co 0 don");
        assertEquals(List.of(OrderMapper.WIRE_STATUS_PENDING, OrderMapper.WIRE_STATUS_CONFIRMED,
                        OrderMapper.WIRE_STATUS_SHIPPING, OrderMapper.WIRE_STATUS_DELIVERED,
                        OrderMapper.WIRE_STATUS_CANCELLED),
                columns.stream().map(AdminOverviewResponse.StatusCountResponse::getStatus).toList(),
                "Thu tu phai la thu tu vong doi cua mot don");
        assertEquals(4, columns.stream().filter(column -> column.getCount() == 0L).count(),
                "Bon trang thai con lai phai co count=0 — bang chung zero-fill co that");
        assertEquals(2L, columns.get(4).getCount());
    }

    /**
     * <b>Hai bất biến, tính bằng tay từ chính payload.</b>
     * <p>
     * Đây là đúng phép kiểm mà §B.12.4 mô tả cho client, chạy ở đây trên một tập dữ liệu <i>thưa</i>
     * — nơi phần lớn giá trị đến từ bước zero-fill chứ không từ truy vấn.
     */
    @Test
    @DisplayName("revenue == sum(revenueByDay) va orderCount == sum(ordersByStatus)")
    void bothInvariantsHoldOnSparseData() {
        List<LocalDate> window = genWindow(7);
        stubDomain(window,
                List.of(DailyRevenue.of(window.get(1), 100_000L),
                        DailyRevenue.of(window.get(5), 250_000L)),
                List.of(StatusCount.of(OrderDomainService.STATUS_PENDING, 3L),
                        StatusCount.of(OrderDomainService.STATUS_CANCELLED, 1L)));

        AdminOverviewResponse overview = statsAppService.findOverview(7);

        long sumOfDays = overview.getRevenueByDay().stream()
                .mapToLong(AdminOverviewResponse.DailyRevenueResponse::getRevenue)
                .sum();
        long sumOfStatuses = overview.getOrdersByStatus().stream()
                .mapToLong(AdminOverviewResponse.StatusCountResponse::getCount)
                .sum();

        assertEquals(350_000L, overview.getRevenue(), "revenue cong tu chinh revenueByDay");
        assertEquals(sumOfDays, overview.getRevenue());
        assertEquals(4L, overview.getOrderCount(), "orderCount cong tu chinh ordersByStatus");
        assertEquals(sumOfStatuses, overview.getOrderCount());
    }

    /**
     * <b>{@code customerCount} và {@code lowStockCount} KHÔNG phụ thuộc {@code days}</b> (§B.12.4)
     * — chúng là ảnh chụp hiện tại.
     * <p>
     * Cùng một cặp mock trả về cùng con số cho mọi {@code days}, và ca này khẳng định tầng
     * application không nhân/chia/cắt gì lên chúng.
     */
    @Test
    @DisplayName("customerCount va lowStockCount la anh chup, khong doi theo `days`")
    void snapshotCountsDoNotDependOnDays() {
        List<LocalDate> window7 = genWindow(7);
        List<LocalDate> window30 = genWindow(30);
        when(orderDomainService.genDateWindow(7)).thenReturn(window7);
        when(orderDomainService.genDateWindow(30)).thenReturn(window30);
        when(orderDomainService.findRevenueByDay(any(), any())).thenReturn(List.of());
        when(orderDomainService.countOrdersByStatus(any(), any())).thenReturn(List.of());
        when(userDomainService.countCustomers()).thenReturn(6L);
        when(productDomainService.countLowStockProducts()).thenReturn(1L);

        for (int days : new int[]{7, 30}) {
            AdminOverviewResponse overview = statsAppService.findOverview(days);
            assertEquals(6L, overview.getCustomerCount());
            assertEquals(1L, overview.getLowStockCount());
            // Va khi khong co don nao thi hai tong deu bang 0 — nhung mang van day
            assertEquals(0L, overview.getRevenue());
            assertEquals(0L, overview.getOrderCount());
            assertEquals(days, overview.getRevenueByDay().size());
            assertEquals(5, overview.getOrdersByStatus().size());
        }
    }

    /**
     * Dựng khung ngày kết thúc ở hôm nay — <b>khớp đúng thứ {@code genDateWindow} thật trả về</b>,
     * vì ca này mock domain service.
     *
     * @param days số ngày
     * @return danh sách ngày tăng dần
     */
    private List<LocalDate> genWindow(int days) {
        LocalDate lastDay = LocalDate.now(OrderDomainService.STORE_ZONE);
        List<LocalDate> window = new ArrayList<>(days);
        for (int index = 0; index < days; index++) {
            window.add(lastDay.minusDays(days - 1L - index));
        }
        return window;
    }

    /**
     * Nối ba domain service giả cho một khung ngày và một tập kết quả <b>thưa</b>.
     *
     * @param window khung ngày
     * @param revenues các dòng doanh thu có thật
     * @param statuses các dòng trạng thái có thật
     */
    private void stubDomain(List<LocalDate> window, List<DailyRevenue> revenues,
                            List<StatusCount> statuses) {
        when(orderDomainService.genDateWindow(anyInt())).thenReturn(window);
        when(orderDomainService.findRevenueByDay(any(), any())).thenReturn(revenues);
        when(orderDomainService.countOrdersByStatus(any(), any())).thenReturn(statuses);
        when(userDomainService.countCustomers()).thenReturn(6L);
        when(productDomainService.countLowStockProducts()).thenReturn(1L);
    }
}
