package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.StatusCount;
import com.nss.ddd.domain.model.entity.Order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data interface của {@code customer_order} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * <b>Cả hai đường đọc đều {@code LEFT JOIN FETCH} {@code user}, và lý do phải viết ra:</b>
 * {@code open-in-view: false} nên session đóng ngay khi repository trả về, còn
 * {@code OrderResponse.userId} thì cần đọc id của chủ đơn. Quan hệ này {@code nullable} nên
 * Hibernate không dùng được proxy rẻ tiền cho nó — để lazy thì mỗi đơn trong danh sách sinh thêm
 * một truy vấn, đúng kiểu N+1 mà {@code GET /orders/me} (trả mảng <b>không phân trang</b>) khuếch
 * đại lên theo số đơn của khách. {@code @ManyToOne} nên fetch join không nhân bản dòng.
 * <p>
 * <b>Đường liệt kê chéo người dùng có ĐÚNG MỘT method: {@link #findAdminPage}</b>, và nó là một
 * method <i>riêng</i> chứ không phải một tham số thêm vào {@link #findByUserIdWithUser(Long)}.
 * API_CONTRACT §C.4.3b nói việc truy vấn chéo người dùng thuộc namespace {@code /admin} và phải là
 * một endpoint song sinh riêng; giữ hai câu truy vấn tách rời là cách phép tách đó nhìn thấy được
 * ngay ở tầng hạ tầng. Một {@code findAll()} trần thì vẫn không được thêm — nó là thứ ai đó sẽ nối
 * vào {@code /orders/me} bằng một tham số lọc.
 * <p>
 * <b>Hai truy vấn tổng hợp ở cuối file là truy vấn tổng hợp ĐẦU TIÊN của dự án</b> (backlog 0019,
 * §B.12.4). Chúng chia nhau <i>một</i> cặp mốc thời gian và <i>một</i> quy ước biên
 * ({@code >= from}, {@code < to}), vì hai bất biến của hợp đồng —
 * {@code revenue == sum(revenueByDay)} và {@code orderCount == sum(ordersByStatus)} — chỉ đúng khi
 * hai bên nói về đúng một tập đơn.
 */
public interface OrderJPAMapper extends JpaRepository<Order, Long> {

    /**
     * Mệnh đề lọc của {@code GET /admin/orders} — <b>khai một lần, dùng cho cả truy vấn trang lẫn
     * truy vấn đếm</b>.
     * <p>
     * <b>Đây là hằng chứ không phải hai chuỗi chép nhau, và lý do rất cụ thể:</b> Spring Data không
     * suy được câu đếm từ một truy vấn có {@code JOIN FETCH}, nên {@code countQuery} phải viết tay.
     * Hai bản viết tay lệch nhau thì {@code items} và {@code total} nói về hai tập khác nhau, và
     * triệu chứng là một cái nút "trang sau" dẫn tới trang trống. {@code ProductJPAMapper} có cùng
     * cấu trúc này.
     * <p>
     * <b>Ba trường của {@code q} dùng CHUNG một tham số {@code :pattern}</b>, đúng như
     * {@code adminOrders.api.ts:49-57}: mã đơn, họ tên người nhận <i>đã bỏ dấu</i>, và số điện
     * thoại người nhận. Cả ba lấy từ chính đơn hàng chứ không từ hồ sơ tài khoản — đơn khách vãng
     * lai không có tài khoản nào để tra, và người đặt hộ vẫn phải tìm ra đơn theo tên người nhận
     * thật.
     * <p>
     * <b>Một mẫu {@code LIKE} cho cả ba là ĐỦ, không phải một sự lười.</b> Từ khoá đã đi qua
     * {@code TextNormalizer}: chữ số của số điện thoại không đổi, còn mã đơn thì so khớp qua
     * collation {@code utf8mb4_unicode_ci} vốn không phân biệt hoa thường — {@code nss-2026} khớp
     * {@code NSS-20260826-K7M2QX9P4T}. Frontend cũng dùng đúng một từ khoá cho cả ba trường.
     * <p>
     * <b>{@code ESCAPE '!'} chứ không phải gạch chéo ngược</b> — cùng lý do đã ghi ở
     * {@code ProductJPAMapper}: không escape thì {@code q=100%} thành ký tự đại diện và bộ lọc trả
     * về nhiều dòng hơn số dòng thật sự khớp.
     * <p>
     * <b>Điều kiện {@code userId} đi qua alias {@code u} của LEFT JOIN, KHÔNG qua đường dẫn ngầm
     * {@code o.user.id}.</b> Đây là chỗ sai im lặng nguy hiểm nhất của câu truy vấn này: một đường
     * dẫn ngầm qua quan hệ to-one có thể dịch ra <b>INNER JOIN</b>, và INNER JOIN lọc sạch mọi đơn
     * <i>khách vãng lai</i> ({@code user_id IS NULL}) — kể cả khi không lọc theo {@code userId}.
     * Không exception, không cảnh báo, chỉ là thiếu dòng; và seed hiện có 5/11 đơn vãng lai nên
     * hơn một phần ba bảng sẽ biến mất.
     */
    String ADMIN_FILTER = " WHERE (:pattern IS NULL"
            + "      OR o.code LIKE :pattern ESCAPE '!'"
            + "      OR o.shipping.fullNameNormalized LIKE :pattern ESCAPE '!'"
            + "      OR o.shipping.phone LIKE :pattern ESCAPE '!')"
            + " AND (:status IS NULL OR o.status = :status)"
            + " AND (:userId IS NULL OR u.id = :userId)";

    /**
     * Tra đơn theo mã hiển thị — khoá tra cứu của {@code GET /orders/{code}}.
     * <p>
     * <b>So khớp chính xác, cố ý không {@code UPPER()} như {@code CouponJPAMapper}.</b> Mã đơn do
     * backend sinh ra chứ không do người dùng gõ tự do, và nó đi vào URL: hai chuỗi khác hoa thường
     * là hai đường dẫn khác nhau. Nới ra ở đây sẽ khiến một mã gõ sai kiểu vẫn mở được đơn, tức
     * mở rộng bề mặt đoán mã trên một endpoint vốn đã công khai (§Contract 6).
     *
     * @param code mã đơn dạng {@code NSS-20260826-K7M2QX9P4T}
     * @return đơn hàng kèm chủ đơn đã nạp, hoặc rỗng
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user WHERE o.code = :code")
    Optional<Order> findByCodeWithUser(@Param("code") String code);

    /**
     * Các đơn của <b>đúng một</b> người dùng, mới nhất trước.
     * <p>
     * {@code ORDER BY o.createdAt DESC, o.id DESC} — vế thứ hai không phải trang trí: hai đơn đặt
     * trong cùng một giây có {@code created_at} bằng nhau, và không có vế phá hoà thì MySQL được
     * phép đảo thứ tự giữa hai lần gọi, khiến danh sách nhảy chỗ trước mắt người dùng và khiến test
     * so khớp đỏ ngẫu nhiên.
     *
     * @param userId chủ đơn, lấy từ claim {@code sub} của JWT (§C.4.1)
     * @return các đơn của người dùng này kèm chủ đơn đã nạp
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user"
            + " WHERE o.user.id = :userId"
            + " ORDER BY o.createdAt DESC, o.id DESC")
    List<Order> findByUserIdWithUser(@Param("userId") Long userId);

    /**
     * Một trang đơn hàng <b>của mọi người dùng</b>, có lọc — đường đọc của
     * {@code GET /admin/orders} (§B.12.2).
     * <p>
     * <b>{@code ORDER BY} nằm trong chuỗi truy vấn chứ không đi qua {@code Sort}</b> — ngược với
     * {@code ProductJPAMapper.findAdminPage}, và khác biệt đó phản chiếu đúng hợp đồng: §B.12.2
     * <i>không có</i> tham số {@code sort}, thứ tự là cố định. Client không chọn được thì không có
     * gì để truyền qua {@code Pageable}.
     * <p>
     * <b>Khoá phụ {@code o.id DESC} không phải trang trí.</b> Backlog 0014 cắt {@code created_at}
     * tới <b>giây</b>, nên hai đơn trùng mốc là chuyện thường — seed hiện có sẵn một cặp
     * ({@code NSS-20260825-0007} và {@code NSS-20260825-0008}, cùng {@code 07:32:35}). Không có vế
     * phá hoà thì với phân trang {@code OFFSET}, MySQL được phép trả một dòng ở cả trang 1 lẫn
     * trang 2 và bỏ sót một dòng khác, <b>mà không có gì báo lỗi</b>.
     * <p>
     * {@code LEFT JOIN FETCH o.user} vì {@code OrderResponse.userId} cần id của chủ đơn và
     * {@code open-in-view: false} đóng session ngay khi repository trả về — xem javadoc interface.
     *
     * @param pattern mẫu {@code LIKE} đã bọc {@code %} và đã escape; {@code null} là không tìm
     * @param status con số trạng thái {@code 0..4}; {@code null} là không lọc
     * @param userId chủ đơn; {@code null} là không lọc
     * @param pageable trang cần lấy, <b>đã đánh số từ 0</b>; <b>không</b> mang {@code Sort}
     * @return trang đơn hàng kèm tổng số dòng khớp điều kiện
     */
    @Query(value = "SELECT o FROM Order o LEFT JOIN FETCH o.user u" + ADMIN_FILTER
            + " ORDER BY o.createdAt DESC, o.id DESC",
            countQuery = "SELECT COUNT(o) FROM Order o LEFT JOIN o.user u" + ADMIN_FILTER)
    Page<Order> findAdminPage(@Param("pattern") String pattern,
                              @Param("status") Integer status,
                              @Param("userId") Long userId,
                              Pageable pageable);

    /**
     * Doanh thu gom theo <b>ngày cửa hàng</b> — nửa đầu của {@code GET /admin/stats/overview}
     * (§B.12.4).
     * <p>
     * <b>Native, và đây là ca native đúng chỗ:</b> quy đổi múi giờ trong lúc gom nhóm không biểu
     * diễn được bằng JPQL. {@code CONVERT_TZ} với <i>độ lệch dạng số</i> ({@code '+07:00'}) chạy
     * được trên MySQL trần, không cần bảng múi giờ đã nạp — đã đo trên chính container của dự án:
     * {@code CONVERT_TZ('2026-08-25 13:00:00','+00:00','+07:00')} ra {@code 2026-08-25 20:00:00}.
     * <p>
     * <b>Độ lệch là THAM SỐ, không viết cứng vào chuỗi.</b> Nó do domain truyền xuống từ hằng
     * {@code OrderDomainService.STORE_ZONE} — hạ tầng không cần biết cửa hàng đặt ở đâu, và một
     * chuỗi {@code '+07:00'} viết cứng ở đây là bản sao thứ hai của cùng một sự thật.
     * <p>
     * <b>Quy đổi múi giờ nằm TRONG mệnh đề gom nhóm, còn khoảng thời gian thì so trên cột UTC
     * nguyên bản.</b> Hai mốc {@code fromUtc} / {@code toUtc} chính là 00:00 giờ cửa hàng của ngày
     * đầu và của ngày <i>sau</i> ngày cuối, đã quy về UTC ở domain — nên "đơn này có trong khoảng
     * không" và "đơn này rơi vào cột nào" vẫn do một phép tính quyết định, mà câu SQL thì so trên
     * một cột <b>có index</b> ({@code idx_created_at}) thay vì trên một biểu thức.
     * <p>
     * <b>Biên trên MỞ ({@code < :toUtc}).</b> Dùng {@code <=} trên 23:59:59 sẽ bỏ sót mọi đơn rơi
     * vào phần lẻ dưới giây.
     * <p>
     * <b>Loại đơn {@code cancelled}</b> (§B.12.4) — đơn huỷ đã xảy ra nên nó vẫn vào
     * {@code orderCount} và vào cột {@code cancelled} của {@link #countByStatus}, nhưng nó không
     * phải tiền cửa hàng thu được. Con số trạng thái truyền xuống làm tham số vì nó là kiến thức
     * nghiệp vụ, không phải của tầng SQL.
     * <p>
     * <b>Bảng ánh xạ {@code Object[]} theo vị trí</b> (coding-conventions §7 — chỉ được dùng cho
     * native query, và phải có comment ngay cạnh):
     * <ul>
     *   <li>{@code [0]} — {@code order_date}, ngày theo giờ cửa hàng ({@code java.sql.Date});</li>
     *   <li>{@code [1]} — {@code revenue}, tổng {@code total} của ngày đó ({@code BigDecimal}, vì
     *       {@code SUM()} của MySQL trả kiểu rộng hơn cột).</li>
     * </ul>
     * Phép đọc theo vị trí nằm ở {@code OrderRepositoryImpl} — sửa thứ tự cột ở đây thì phải sửa
     * cả bên đó trong cùng lần sửa.
     *
     * @param fromUtc mốc đầu khoảng, giờ UTC, đã bao gồm
     * @param toUtc mốc cuối khoảng, giờ UTC, <b>không</b> bao gồm
     * @param storeOffset độ lệch múi giờ cửa hàng dạng {@code +07:00}
     * @param cancelledStatus con số trạng thái {@code cancelled}
     * @return các dòng {@code (ngày, doanh thu)}, tăng dần theo ngày; <b>thưa</b> — ngày không có
     *         đơn thì không có dòng
     */
    @Query(value = "SELECT DATE(CONVERT_TZ(o.created_at, '+00:00', :storeOffset)) AS order_date,"
            + " SUM(o.total) AS revenue"
            + " FROM customer_order o"
            + " WHERE o.created_at >= :fromUtc AND o.created_at < :toUtc"
            + " AND o.status <> :cancelledStatus"
            + " GROUP BY order_date"
            + " ORDER BY order_date ASC",
            nativeQuery = true)
    List<Object[]> sumRevenueByDay(@Param("fromUtc") LocalDateTime fromUtc,
                                   @Param("toUtc") LocalDateTime toUtc,
                                   @Param("storeOffset") String storeOffset,
                                   @Param("cancelledStatus") int cancelledStatus);

    /**
     * Số đơn gom theo trạng thái — nửa sau của {@code GET /admin/stats/overview} (§B.12.4).
     * <p>
     * <b>JPQL chứ không native, khác {@link #sumRevenueByDay}</b>, và khác biệt đó có lý do: ở đây
     * không có phép quy đổi múi giờ nào để phải mượn cú pháp MySQL — gom nhóm theo một cột
     * {@code int} thì JPQL làm được, và coding-conventions §12 đặt JPQL làm mặc định.
     * <p>
     * <b>Dựng thẳng {@code StatusCount} bằng constructor expression</b> thay vì trả {@code Object[]}:
     * §7 chỉ cho phép map theo vị trí ở native query, và ở đây không cần — kiểu kết quả do domain
     * sở hữu nên JPQL gọi được thẳng constructor của nó.
     * <p>
     * <b>KHÔNG loại trạng thái nào.</b> Đơn huỷ vẫn được đếm — nó đã xảy ra. Cùng cặp mốc thời gian
     * với {@link #sumRevenueByDay}, nhờ đó bất biến {@code orderCount == sum(ordersByStatus)} đúng
     * theo cấu tạo.
     *
     * @param fromUtc mốc đầu khoảng, giờ UTC, đã bao gồm
     * @param toUtc mốc cuối khoảng, giờ UTC, <b>không</b> bao gồm
     * @return số đơn theo trạng thái, tăng dần theo con số trạng thái; <b>thưa</b> — trạng thái
     *         không có đơn thì không có dòng
     */
    @Query("SELECT new com.nss.ddd.domain.model.StatusCount(o.status, COUNT(o))"
            + " FROM Order o"
            + " WHERE o.createdAt >= :fromUtc AND o.createdAt < :toUtc"
            + " GROUP BY o.status"
            + " ORDER BY o.status ASC")
    List<StatusCount> countByStatus(@Param("fromUtc") LocalDateTime fromUtc,
                                    @Param("toUtc") LocalDateTime toUtc);
}
