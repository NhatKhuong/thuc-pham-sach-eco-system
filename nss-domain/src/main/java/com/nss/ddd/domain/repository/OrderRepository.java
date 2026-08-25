package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.DailyRevenue;
import com.nss.ddd.domain.model.OrderFilter;
import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.StatusCount;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.OrderStatusHistory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * PORT của aggregate {@code Order} — domain khai báo, infrastructure implement.
 * <p>
 * <b>Ràng buộc kiến trúc:</b> file này không được import bất cứ thứ gì thuộc
 * {@code org.springframework.data.*}, giống {@link ProductRepository} và {@link CouponRepository}.
 * Mất ranh giới này là mất lý do chia module (architecture/01-overview.md §1).
 * <p>
 * <b>Ba bảng, một aggregate.</b> {@code customer_order}, {@code order_item} và
 * {@code order_status_history} chỉ có nghĩa cùng nhau: một đơn không có dòng hàng là chứng từ rỗng,
 * và một đơn không có dòng lịch sử đầu tiên là đơn không ai biết đã ra đời lúc nào. Vì vậy chúng
 * chung một port và <b>bắt buộc</b> ghi trong cùng một transaction (backlog 0014 §Contract 9) —
 * tách thành ba port là mời ba lần ghi rời nhau.
 * <p>
 * <b>Đường đọc chéo người dùng có ĐÚNG MỘT lối vào: {@link #findAdminPage(OrderFilter)}.</b>
 * Backlog 0019 mở nó cho namespace {@code /admin} (API_CONTRACT §C.4.3b) — và nó là port
 * <i>riêng</i>, tách khỏi {@link #findByUserId(Long)}, đúng như javadoc trước đó đã dự liệu. Một
 * {@code findAll()} trần thì vẫn <b>không</b> được thêm: {@code findAdminPage} luôn đi kèm phân
 * trang và luôn đi qua một {@code OrderFilter}, nên nó không dùng lại được cho {@code /orders/me}
 * bằng một tham số lọc — đúng cái cửa mà §C.4.1 đóng lại.
 */
public interface OrderRepository {

    /**
     * Ghi đơn hàng (chèn mới khi {@code id} rỗng, cập nhật khi đã có).
     *
     * @param order đơn hàng cần ghi
     * @return bản ghi sau khi ghi, đã có id
     */
    Order save(Order order);

    /**
     * Ghi một loạt dòng hàng của <b>cùng một</b> đơn.
     *
     * @param items các dòng hàng, đã gắn sẵn {@code order}
     * @return các bản ghi sau khi ghi, đã có id
     */
    List<OrderItem> saveItems(List<OrderItem> items);

    /**
     * Ghi một dòng nhật ký chuyển trạng thái.
     *
     * @param history dòng nhật ký, đã gắn sẵn {@code order}
     * @return bản ghi sau khi ghi, đã có id
     */
    OrderStatusHistory saveHistory(OrderStatusHistory history);

    /**
     * Tra đơn theo <b>mã đơn</b> — khoá tra cứu của {@code GET /orders/{code}} (§B.6).
     * <p>
     * Khoá theo {@code code} chứ không theo {@code id} vì mã đơn là thứ duy nhất nhân viên và khách
     * cùng đọc được qua điện thoại; {@code id} không bao giờ rời khỏi cơ sở dữ liệu.
     *
     * @param code mã đơn dạng {@code NSS-20260817-0001}
     * @return đơn hàng, hoặc rỗng khi không có mã nào như vậy
     */
    Optional<Order> findByCode(String code);

    /**
     * Các đơn của <b>đúng một</b> người dùng — đầu vào của {@code GET /orders/me} (§C.4.1).
     * <p>
     * <b>Không phân trang</b>: hợp đồng §B.6 chốt {@code Order[]} trần. Thứ tự là
     * {@code createdAt} giảm dần rồi {@code id} giảm dần — đơn mới trước, và vế {@code id} giữ thứ
     * tự <b>ổn định</b> khi hai đơn trùng mốc thời gian (một khách bấm đặt hai lần trong cùng giây
     * là chuyện có thật, và không có vế thứ hai thì MySQL được phép đảo thứ tự giữa hai lần gọi).
     *
     * @param userId chủ đơn, lấy từ claim {@code sub} của JWT — <b>không bao giờ</b> từ query,
     *               path hay body (§C.4.1)
     * @return các đơn của người dùng này; danh sách rỗng khi chưa có đơn nào
     */
    List<Order> findByUserId(Long userId);

    /**
     * Dòng hàng của <b>nhiều</b> đơn trong một lượt — chống N+1 cho {@code GET /orders/me}.
     * <p>
     * Tồn tại vì hợp đồng trả {@code Order[]} không phân trang: hỏi dòng hàng cho từng đơn biến một
     * khách hàng có 30 đơn thành 31 lượt đi vòng tới MySQL, trên một endpoint mà frontend gọi mỗi
     * lần khách mở trang "Đơn hàng của tôi".
     * <p>
     * Thứ tự trả về sắp theo {@code order.id} rồi {@code id} tăng dần, nhờ đó phía gọi gom nhóm ra
     * được danh sách dòng hàng <b>ổn định</b> giữa các lần gọi.
     *
     * @param orderIds khoá chính của các đơn; {@code null} hoặc rỗng cho ra danh sách rỗng
     * @return các dòng hàng thuộc những đơn đó; danh sách rỗng khi không khớp dòng nào
     */
    List<OrderItem> findItemsByOrderIds(Collection<Long> orderIds);

    /**
     * Một trang đơn hàng <b>của mọi người dùng</b>, có lọc — đường đọc của
     * {@code GET /admin/orders} (§B.12.2).
     * <p>
     * <b>Thứ tự cố định {@code createdAt} giảm dần rồi {@code id} giảm dần</b>, không có tham số
     * {@code sort}. Vế {@code id} không phải trang trí: backlog 0014 cắt {@code created_at} tới
     * <b>giây</b>, nên hai đơn trùng mốc là chuyện thường (seed hiện có sẵn một cặp), và với phân
     * trang {@code OFFSET} thì thiếu khoá phụ nghĩa là một dòng hiện ở cả trang 1 lẫn trang 2 trong
     * khi một dòng khác không hiện ở đâu — <b>không có gì báo lỗi</b>.
     *
     * @param filter điều kiện lọc; {@code keyword} đã được domain service bỏ dấu
     * @return trang đơn hàng kèm tổng số dòng khớp điều kiện
     */
    PageResult<Order> findAdminPage(OrderFilter filter);

    /**
     * Doanh thu gom theo <b>ngày cửa hàng</b> — truy vấn tổng hợp của
     * {@code GET /admin/stats/overview} (§B.12.4).
     * <p>
     * <b>Chỉ cộng đơn KHÔNG ở trạng thái {@code cancelled}</b> (§B.12.4): đơn huỷ đã xảy ra nên nó
     * vẫn vào {@code orderCount} và vào cột {@code cancelled}, nhưng nó không phải tiền cửa hàng
     * thu được.
     * <p>
     * <b>Trả về THƯA — chỉ những ngày có đơn.</b> Zero-fill là việc của tầng application.
     *
     * @param fromUtc mốc đầu khoảng, <b>giờ UTC</b>, đã bao gồm
     * @param toUtc mốc cuối khoảng, <b>giờ UTC</b>, <b>không</b> bao gồm
     * @param storeOffset độ lệch múi giờ cửa hàng dạng {@code +07:00} — domain truyền xuống để
     *                    adapter không phải biết cửa hàng đặt ở đâu
     * @param cancelledStatus con số trạng thái {@code cancelled}, truyền xuống vì nó là kiến thức
     *                        nghiệp vụ chứ không phải của tầng SQL
     * @return doanh thu theo ngày, tăng dần; danh sách rỗng khi khoảng không có đơn nào
     */
    List<DailyRevenue> sumRevenueByDay(LocalDateTime fromUtc, LocalDateTime toUtc,
                                       String storeOffset, int cancelledStatus);

    /**
     * Số đơn gom theo trạng thái trong một khoảng — truy vấn tổng hợp của
     * {@code GET /admin/stats/overview} (§B.12.4).
     * <p>
     * <b>Đếm MỌI trạng thái, kể cả {@code cancelled}</b> — xem {@link #sumRevenueByDay}. Cùng một
     * cặp mốc thời gian với nó, nhờ đó bất biến {@code orderCount == sum(ordersByStatus[].count)}
     * đúng theo cấu tạo.
     * <p>
     * <b>Trả về THƯA — chỉ những trạng thái có đơn.</b> Bù đủ năm trạng thái là việc của tầng
     * application.
     *
     * @param fromUtc mốc đầu khoảng, <b>giờ UTC</b>, đã bao gồm
     * @param toUtc mốc cuối khoảng, <b>giờ UTC</b>, <b>không</b> bao gồm
     * @return số đơn theo trạng thái; danh sách rỗng khi khoảng không có đơn nào
     */
    List<StatusCount> countByStatus(LocalDateTime fromUtc, LocalDateTime toUtc);

    /**
     * Tổng số đơn đang có trong bảng — đầu vào duy nhất của số thứ tự trong mã đơn (§Contract 6).
     * <p>
     * <b>Đếm trên TOÀN BỘ bảng, không đếm theo ngày.</b> Owner chốt giữ dạng tuần tự toàn cục ở
     * §Contract 6, nên hai đơn đặt cách nhau một ngày vẫn nhận hai số liền nhau; phần
     * {@code YYYYMMDD} của mã chỉ nói đơn ra đời hôm nào chứ không mở một dãy số mới.
     *
     * @return số dòng hiện có trong {@code customer_order}
     */
    long countOrders();
}
