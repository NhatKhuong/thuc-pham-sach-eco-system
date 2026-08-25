package com.nss.ddd.application.service.stats.impl;

import com.nss.ddd.application.mapper.OrderMapper;
import com.nss.ddd.application.model.response.AdminOverviewResponse;
import com.nss.ddd.application.service.stats.StatsAppService;
import com.nss.ddd.domain.model.DailyRevenue;
import com.nss.ddd.domain.model.StatusCount;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.domain.service.ProductDomainService;
import com.nss.ddd.domain.service.UserDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hiện thực use case số liệu tổng quan.
 * <p>
 * <b>Đây là chỗ zero-fill xảy ra, và nó KHÔNG được đẩy xuống SQL</b> (§B.12.4). Hai truy vấn tổng
 * hợp trả về kết quả <i>thưa</i> — chỉ những ngày có đơn, chỉ những trạng thái có đơn — vì đó là
 * tất cả những gì cơ sở dữ liệu biết. Khung {@code days} ngày liên tiếp và bộ đủ 5 trạng thái là
 * <i>hình dạng của bề mặt dây</i>, nên chúng được dựng ở đây.
 * <p>
 * <b>{@code revenue} được cộng TỪ CHÍNH {@code revenueByDay}, không phải từ một truy vấn thứ hai —
 * và đó là điều quan trọng nhất của cả file này.</b> Hợp đồng đòi
 * {@code revenue == sum(revenueByDay[].revenue)}; frontend giữ điều đó đúng bằng cách cộng từ mảng
 * ({@code adminStats.api.ts:136} — {@code revenueByDay.reduce(...)}), và backend làm y hệt. Hai
 * truy vấn độc lập rồi khẳng định chúng bằng nhau là tự đặt một cái bẫy: chúng sẽ lệch vào đúng lúc
 * một bên được sửa mệnh đề lọc, và <b>không có gì báo lỗi</b> — chỉ có một ô chỉ số không khớp tổng
 * của biểu đồ ngay bên dưới nó.
 * <p>
 * Bất biến thứ hai — {@code orderCount == sum(ordersByStatus[].count)} — được giữ theo cùng cách:
 * {@code orderCount} cộng từ chính mảng đã zero-fill.
 * <p>
 * <b>Không {@code @Transactional}:</b> bốn truy vấn độc lập chỉ đọc, mỗi cái trả về một con số hoặc
 * một danh sách nhỏ. coding-conventions §8 mục 5 cấm khai {@code readOnly} khi không viết ra được
 * lý do.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAppServiceImpl implements StatsAppService {

    /**
     * Khuôn {@code date} của {@code revenueByDay} — {@code YYYY-MM-DD} (§B.12.4).
     * <p>
     * {@code ISO_LOCAL_DATE} chứ không một pattern viết tay: nó <i>chính là</i> {@code YYYY-MM-DD},
     * và nó không có ô nào để gõ nhầm {@code YYYY} thành {@code yyyy} (hai thứ khác nhau ở tuần
     * cuối năm, và khác đúng một ngày trong năm — kiểu lỗi mỗi năm mới lộ ra một lần).
     */
    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Năm trạng thái, <b>theo đúng thứ tự vòng đời của một đơn</b> — khung zero-fill của
     * {@code ordersByStatus}.
     * <p>
     * Suy từ năm hằng của {@code OrderDomainService} chứ không viết lại con số: thêm một trạng thái
     * thứ sáu mà quên sửa mảng này thì cột mới lặng lẽ vắng mặt khỏi biểu đồ, và không có gì báo
     * lỗi. Thứ tự khớp {@code ORDER_STATUSES} của frontend (suy từ {@code ORDER_STATUS_LABELS}).
     */
    private static final int[] ALL_STATUSES = {
            OrderDomainService.STATUS_PENDING,
            OrderDomainService.STATUS_CONFIRMED,
            OrderDomainService.STATUS_SHIPPING,
            OrderDomainService.STATUS_DELIVERED,
            OrderDomainService.STATUS_CANCELLED
    };

    private final OrderDomainService orderDomainService;

    private final UserDomainService userDomainService;

    private final ProductDomainService productDomainService;

    @Override
    public AdminOverviewResponse findOverview(int days) {
        // 1. Khung ngay theo gio CUA HANG. No vua la khung zero-fill, vua la dinh nghia cua khoang
        //    thoi gian ma ca bon so phu thuoc `days` dung chung.
        List<LocalDate> window = orderDomainService.genDateWindow(days);
        LocalDate firstDay = window.get(0);
        LocalDate lastDay = window.get(window.size() - 1);

        // 2. Doanh thu theo ngay — ket qua THUA, roi zero-fill cho du `days` phan tu
        Map<LocalDate, Long> revenueByDate = new HashMap<>();
        for (DailyRevenue row : orderDomainService.findRevenueByDay(firstDay, lastDay)) {
            revenueByDate.put(row.getDate(), row.getRevenue());
        }
        List<AdminOverviewResponse.DailyRevenueResponse> revenueByDay = new ArrayList<>(window.size());
        long revenue = 0L;
        for (LocalDate date : window) {
            long dailyRevenue = revenueByDate.getOrDefault(date, 0L);
            // `revenue` cong TU CHINH mang nay — xem javadoc cap class
            revenue += dailyRevenue;
            revenueByDay.add(new AdminOverviewResponse.DailyRevenueResponse()
                    .setDate(DATE_KEY.format(date))
                    .setRevenue(dailyRevenue));
        }

        // 3. Don theo trang thai — ket qua THUA, roi bu du ca 5 trang thai
        Map<Integer, Long> countByStatus = new HashMap<>();
        for (StatusCount row : orderDomainService.countOrdersByStatus(firstDay, lastDay)) {
            countByStatus.put(row.getStatus(), row.getCount());
        }
        List<AdminOverviewResponse.StatusCountResponse> ordersByStatus =
                new ArrayList<>(ALL_STATUSES.length);
        long orderCount = 0L;
        for (int status : ALL_STATUSES) {
            long count = countByStatus.getOrDefault(status, 0L);
            // `orderCount` cong TU CHINH mang nay, cung ly do voi `revenue`
            orderCount += count;
            ordersByStatus.add(new AdminOverviewResponse.StatusCountResponse()
                    .setStatus(OrderMapper.toWireStatus(status))
                    .setCount(count));
        }

        // 4. Hai anh chup hien tai — KHONG phu thuoc `days` (§B.12.4). Ca hai di qua dung mot menh
        //    de loc voi hai endpoint danh sach tuong ung, nen chung bang `total` cua chung theo cau tao.
        long customerCount = userDomainService.countCustomers();
        long lowStockCount = productDomainService.countLowStockProducts();

        log.info("findOverview: success | days={} from={} to={} revenue={} orderCount={} "
                        + "customerCount={} lowStockCount={}",
                days, firstDay, lastDay, revenue, orderCount, customerCount, lowStockCount);
        return new AdminOverviewResponse()
                .setRevenue(revenue)
                .setOrderCount(orderCount)
                .setCustomerCount(customerCount)
                .setLowStockCount(lowStockCount)
                .setRevenueByDay(revenueByDay)
                .setOrdersByStatus(ordersByStatus);
    }
}
