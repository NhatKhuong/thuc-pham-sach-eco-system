package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.DailyRevenue;
import com.nss.ddd.domain.model.OrderFilter;
import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.StatusCount;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.OrderStatusHistory;
import com.nss.ddd.domain.repository.OrderRepository;
import com.nss.ddd.infrastructure.persistence.mapper.OrderItemJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderStatusHistoryJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * ADAPTER cho port {@code OrderRepository}.
 * <p>
 * Đây là <b>ranh giới</b>: mọi khái niệm của Spring Data dừng lại ở file này, phía trên chỉ thấy
 * kiểu của domain.
 * <p>
 * <b>Ba {@code *JPAMapper} cho một port là đúng, không phải thừa.</b> Ba bảng của aggregate
 * {@code Order} chỉ có nghĩa cùng nhau nên chúng chung một port (xem javadoc của port đó), nhưng
 * Spring Data ràng một interface với đúng một entity — nên bên dưới ranh giới vẫn phải là ba
 * interface. Chính vì vậy việc gộp chúng lại phải xảy ra ở <i>đây</i>, chứ không ở tầng service:
 * một service cầm ba mapper là một service biết ba bảng.
 * <p>
 * Stereotype là {@code @Repository}, không phải {@code @Service} (coding-conventions §3).
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    /**
     * Vị trí cột {@code order_date} trong {@code Object[]} của
     * {@code OrderJPAMapper.sumRevenueByDay}.
     * <p>
     * Khai thành hằng thay vì viết {@code row[0]} rải rác: thứ tự cột của một native query là
     * <b>load-bearing</b> và nó nằm ở một file khác, nên chỗ đọc phải nói ra mình đang đọc cột nào.
     */
    /** Ký tự escape của mệnh đề {@code LIKE}; phải khớp {@code ESCAPE} khai trong JPQL. */
    private static final char LIKE_ESCAPE = '!';

    private static final int COLUMN_ORDER_DATE = 0;

    /** Vị trí cột {@code revenue} — xem {@link #COLUMN_ORDER_DATE}. */
    private static final int COLUMN_REVENUE = 1;

    private final OrderJPAMapper orderJPAMapper;

    private final OrderItemJPAMapper orderItemJPAMapper;

    private final OrderStatusHistoryJPAMapper orderStatusHistoryJPAMapper;

    @Override
    public Order save(Order order) {
        return orderJPAMapper.save(order);
    }

    @Override
    public List<OrderItem> saveItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return orderItemJPAMapper.saveAll(items);
    }

    @Override
    public OrderStatusHistory saveHistory(OrderStatusHistory history) {
        return orderStatusHistoryJPAMapper.save(history);
    }

    @Override
    public Optional<Order> findByCode(String code) {
        return orderJPAMapper.findByCodeWithUser(code);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderJPAMapper.findByUserIdWithUser(userId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Phép chặn danh sách rỗng nằm ở đây</b>, cùng khuôn với
     * {@code ProductRepositoryImpl.findByIds} và cùng lý do: {@code IN :orderIds} với collection
     * rỗng dịch ra {@code in ()}, mà MySQL từ chối cú pháp đó. Chặn tại adapter chứ không ở domain
     * vì đây là ràng buộc của <i>SQL</i>, không phải một quy tắc nghiệp vụ — câu "không có đơn nào
     * thì không có dòng hàng nào" đúng ở mọi cơ sở dữ liệu.
     */
    @Override
    public List<OrderItem> findItemsByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderItemJPAMapper.findByOrderIdIn(orderIds);
    }

    // ========== KHU QUAN TRI (§B.12.2, §B.12.4) ==========

    /**
     * {@inheritDoc}
     * <p>
     * Hai phép dịch từ khái niệm của domain sang khái niệm của Spring Data, cả hai chỉ được xảy ra
     * ở file này: từ khoá thành mẫu {@code LIKE}, và {@code page} đánh số từ 1 thành trang đánh số
     * từ 0.
     * <p>
     * <b>{@code PageRequest} ở đây KHÔNG mang {@code Sort}</b>, khác
     * {@code ProductRepositoryImpl.findAdminPage}: thứ tự của {@code GET /admin/orders} là cố định
     * và đã nằm trong chuỗi JPQL. Truyền thêm một {@code Sort} sẽ cho ra hai mệnh đề
     * {@code order by} chồng nhau.
     */
    @Override
    public PageResult<Order> findAdminPage(OrderFilter filter) {
        // API_CONTRACT §A.4: `page` tren duong day danh so tu 1, Spring Data danh so tu 0.
        Page<Order> result = orderJPAMapper.findAdminPage(
                genLikePattern(filter.getKeyword()),
                filter.getStatus(),
                filter.getUserId(),
                PageRequest.of(filter.getPage() - 1, filter.getLimit()));
        return PageResult.of(result.getContent(), result.getTotalElements());
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Đây là chỗ duy nhất đọc {@code Object[]} theo vị trí</b> (coding-conventions §7 cho phép
     * đúng ở native query, kèm comment). Thứ tự cột do
     * {@code OrderJPAMapper.sumRevenueByDay} quyết định — sửa một bên thì phải sửa cả hai.
     * <p>
     * <b>{@code SUM()} của MySQL trả về {@code BigDecimal} chứ không phải {@code Long}</b>, kể cả
     * khi cột nguồn là {@code BIGINT}: engine mở rộng kiểu để tổng không tràn. Ép thẳng sang
     * {@code Long} sẽ ném {@code ClassCastException} ở dòng đầu tiên có dữ liệu — tức chạy được
     * trên một database rỗng và hỏng ngay khi có đơn thật.
     */
    @Override
    public List<DailyRevenue> sumRevenueByDay(LocalDateTime fromUtc, LocalDateTime toUtc,
                                              String storeOffset, int cancelledStatus) {
        List<Object[]> rows = orderJPAMapper.sumRevenueByDay(fromUtc, toUtc, storeOffset, cancelledStatus);
        List<DailyRevenue> revenues = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // [0] = order_date (java.sql.Date), [1] = revenue (BigDecimal) — xem javadoc cua query
            LocalDate date = ((Date) row[COLUMN_ORDER_DATE]).toLocalDate();
            long revenue = ((BigDecimal) row[COLUMN_REVENUE]).longValueExact();
            revenues.add(DailyRevenue.of(date, revenue));
        }
        return revenues;
    }

    @Override
    public List<StatusCount> countByStatus(LocalDateTime fromUtc, LocalDateTime toUtc) {
        return orderJPAMapper.countByStatus(fromUtc, toUtc);
    }

    // ========== DICH KHAI NIEM DOMAIN -> SPRING DATA ==========

    /**
     * Dựng mẫu {@code LIKE} chứa (contains) từ một từ khoá đã bỏ dấu.
     * <p>
     * <b>Bản sao thứ hai của {@code ProductRepositoryImpl.genLikePattern}, và đó là chủ ý — không
     * phải chỗ quên gộp.</b> Thứ §18 cấm chép là <i>phép bỏ dấu</i>, vì nó phải khớp tuyệt đối với
     * giá trị đang nằm trong cột; phép đó đã được gom về {@code TextNormalizer}. Còn việc bọc
     * {@code %} và escape thì thuộc <i>adapter</i> — nó nói về cú pháp {@code LIKE} của câu truy
     * vấn ngay cạnh nó, và gom hai adapter lại sẽ tạo một phụ thuộc chéo giữa hai aggregate chỉ để
     * dùng chung tám dòng.
     * <p>
     * <b>Escape {@code %}, {@code _} và chính ký tự escape trước khi bọc {@code %} hai đầu.</b>
     * Không escape thì {@code q=100%} biến thành ký tự đại diện và trả về nhiều dòng hơn số dòng
     * thật sự khớp — một kết quả <i>sai</i> trông y hệt một kết quả đúng.
     * <p>
     * <b>Không chuẩn hoá lại từ khoá ở đây</b> — nó đã được domain service bỏ dấu.
     *
     * @param keyword từ khoá đã bỏ dấu; {@code null} hoặc rỗng nghĩa là không tìm
     * @return mẫu dạng {@code %tu-khoa%}, hoặc {@code null} khi không tìm
     */
    private static String genLikePattern(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(keyword.length() + 8);
        escaped.append('%');
        for (char character : keyword.toCharArray()) {
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.append('%').toString();
    }
}
