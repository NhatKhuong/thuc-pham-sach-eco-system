package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.DailyRevenue;
import com.nss.ddd.domain.model.OrderFilter;
import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.StatusCount;
import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.OrderStatusHistory;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Domain service của aggregate {@code Order} — nơi ở của phép tính tiền và của trình tự ghi đơn.
 * <p>
 * <b>Thất bại nghiệp vụ ở đây là giá trị trả về, không phải exception</b> (coding-conventions §11
 * Pattern A): {@code false} cho "trừ kho không thành", {@code null} cho "không có đơn nào như vậy".
 * Việc dịch chúng thành mã HTTP là của tầng controller — kiểu {@code *Exception} sống ở module
 * controller (§3) nên domain không thể, và không nên, ném chúng.
 * <p>
 * <b>Service này KHÔNG mở transaction.</b> Toàn bộ luồng tạo đơn phải nằm trong <i>một</i>
 * transaction do tầng application mở (backlog 0014 §Contract 9), nên mỗi method dưới đây là một
 * mảnh của transaction đó chứ không phải một đơn vị công việc độc lập. Đặt {@code @Transactional}
 * lên từng method ở đây sẽ khiến bước trừ kho commit xong trước khi bước ghi đơn kịp hỏng — đúng
 * thứ mà "không có kho bị trừ oan" cấm.
 * <p>
 * <b>Quy tắc mã giảm giá KHÔNG được lặp lại ở đây.</b> Việc "mã này có dùng được không" thuộc
 * {@link CouponDomainService#isRedeemable} và {@link CouponDomainService#meetsMinOrderValue}; file
 * này chỉ <i>tính</i> số tiền được giảm khi câu trả lời đã là có. Cưỡng chế hai lần bằng hai đoạn
 * code là cách sinh ra một đơn được giảm bằng mã mà {@code POST /coupons/validate} vừa từ chối.
 */
public interface OrderDomainService {

    /**
     * Trạng thái đơn vừa tạo — tương ứng chuỗi {@code pending} trên dây (§Contract 4).
     * <p>
     * <b>Năm hằng trạng thái khai ở đây, trong domain, chứ không ở tầng mapper — và đó là chủ ý.</b>
     * Domain là nơi <i>ghi</i> con số xuống cột {@code status} và {@code to_status} lúc tạo đơn,
     * còn mapper của tầng application là nơi <i>dịch</i> nó sang chuỗi. Hai việc đó nói về cùng một
     * bảng ánh xạ; khai hai bản thì một ngày nào đó đơn mới được ghi {@code status = 0} trong khi
     * bảng dịch đã coi {@code 0} là thứ khác, và triệu chứng là một đơn hiển thị sai trạng thái mà
     * không có gì ném lỗi. Chiều phụ thuộc chỉ cho phép một hướng — application thấy domain, domain
     * không thấy application — nên chỗ duy nhất chứa được cả hai người dùng là domain.
     */
    int STATUS_PENDING = 0;

    /** Trạng thái {@code confirmed} (§Contract 4) — xem javadoc {@link #STATUS_PENDING}. */
    int STATUS_CONFIRMED = 1;

    /** Trạng thái {@code shipping} (§Contract 4) — xem javadoc {@link #STATUS_PENDING}. */
    int STATUS_SHIPPING = 2;

    /** Trạng thái {@code delivered} (§Contract 4) — xem javadoc {@link #STATUS_PENDING}. */
    int STATUS_DELIVERED = 3;

    /** Trạng thái {@code cancelled} (§Contract 4) — xem javadoc {@link #STATUS_PENDING}. */
    int STATUS_CANCELLED = 4;

    /**
     * <b>Múi giờ CỬA HÀNG — hằng đầu tiên loại này của dự án</b> (§B.12.4, backlog 0019).
     * <p>
     * Cột {@code created_at} lưu giờ UTC, nhưng "đơn này thuộc ngày nào" là một câu hỏi
     * <i>nghiệp vụ</i> và câu trả lời phải theo giờ nơi cửa hàng bán hàng: một đơn đặt lúc 20:00
     * giờ Việt Nam phải rơi vào <b>đúng ngày đó</b>. Cắt theo UTC sẽ cắt lúc 07:00 giờ Việt Nam,
     * và cột "Ngày đặt" ở bảng đơn sẽ lệch biểu đồ Tổng quan một ngày — <b>không có gì báo lỗi</b>.
     * <p>
     * <b>{@code Asia/Ho_Chi_Minh} chứ không {@code Asia/Saigon}:</b> hai chuỗi trỏ cùng một vùng,
     * nhưng cái sau là bí danh cũ. Chọn tên chuẩn IANA hiện hành. Chuỗi {@code Asia/Saigon} có
     * xuất hiện trong vài comment của repo, nhưng đó là comment — trước ticket này dự án chưa dựng
     * một {@code ZoneId} nào.
     * <p>
     * <b>Nó nằm ở domain vì nó là quy tắc nghiệp vụ.</b> Adapter chỉ nhận độ lệch dạng
     * {@code +07:00} do domain truyền xuống (xem {@code OrderRepository.sumRevenueByDay}) — hạ tầng
     * không cần biết cửa hàng đặt ở đâu.
     */
    ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Định danh tác nhân cho đơn của khách vãng lai, ghi vào {@code order_status_history.changed_by}.
     * <p>
     * Chuỗi trần {@code guest} chứ không {@code null}: cột đó trả lời câu hỏi "ai làm việc này", và
     * với đơn vãng lai thì câu trả lời <i>có thật</i> là "một người chưa đăng nhập" — khác hẳn với
     * "không ai biết", vốn là điều {@code null} nói.
     */
    String CHANGED_BY_GUEST = "guest";

    // ========== TIEN: THU TU TINH LA CONTRACT ==========

    /**
     * Tổng tiền hàng, tính từ <b>giá đã tra lại trong DB</b>.
     * <p>
     * Nhận {@code List<OrderItem>} chứ không nhận dòng giỏ hàng của client là một phép cưỡng chế
     * §C.1: {@code OrderItem.price} chỉ có thể đến từ cột {@code effective_price}, còn con số
     * {@code price} mà client gửi lên thì không có đường nào đi tới method này.
     *
     * @param items các dòng hàng đã dựng từ dữ liệu thật; {@code null} hoặc rỗng cho ra 0
     * @return tổng tiền hàng trước giảm giá, số nguyên VNĐ
     */
    long calcSubtotal(List<OrderItem> items);

    /**
     * Số tiền được giảm, tính trên {@code subtotal} vừa có.
     * <p>
     * <b>Làm tròn HALF_UP</b> cho mã {@code percent} (§Contract 3, coding-conventions §15):
     * {@code Math.round()} của JS là half-up với số dương, nên phía Java phải khai
     * {@code RoundingMode.HALF_UP} <i>tường minh</i> trên {@code BigDecimal}. Một
     * {@code RoundingMode.HALF_EVEN} ở đây làm lệch đúng 1 ₫ ở những đơn rơi vào ranh giới nửa
     * đồng — không có gì ném lỗi, chỉ có tổng tiền không khớp con số frontend vừa hiện.
     * <p>
     * <b>Kết quả không bao giờ vượt quá {@code subtotal}.</b> Một mã {@code fixed} lớn hơn giá trị
     * đơn sẽ cho ra {@code total} âm, tức một chứng từ nói rằng cửa hàng nợ khách tiền. Ngưỡng
     * {@code minOrderValue} thường chặn trước, nhưng "thường" không phải một ràng buộc.
     *
     * @param coupon mã giảm giá đã được xác thực; {@code null} nghĩa là không áp mã
     * @param subtotal tổng tiền hàng, số nguyên VNĐ
     * @return số tiền được giảm, luôn nằm trong khoảng từ 0 tới {@code subtotal}
     */
    long calcDiscount(Coupon coupon, long subtotal);

    /**
     * Phí vận chuyển, tính trên số tiền <b>đã trừ giảm giá</b>.
     * <p>
     * <b>Đây là chỗ sai không nổ ra lỗi nào</b> (§Contract 1): tính trên {@code subtotal} trần thì
     * mọi đơn quanh ngưỡng lệch đúng 30.000 ₫ so với con số {@code orders.api.ts:createOrder} phía
     * frontend vừa hiển thị cho khách, và không có test nào của backend tự phát hiện được.
     *
     * @param amountAfterDiscount hiệu {@code subtotal - discount}, số nguyên VNĐ
     * @return 0 khi đã đạt ngưỡng miễn phí, ngược lại là phí cố định
     */
    long calcShippingFee(long amountAfterDiscount);

    /**
     * @param subtotal tổng tiền hàng
     * @param discount số tiền được giảm
     * @param shippingFee phí vận chuyển
     * @return tổng phải trả, số nguyên VNĐ
     */
    long calcTotal(long subtotal, long discount, long shippingFee);

    /**
     * Sinh mã đơn dạng {@code NSS-YYYYMMDD-NNNN} (§Contract 6).
     * <p>
     * {@code NNNN} là <b>số thứ tự trên toàn bộ bảng</b>, không phải số thứ tự trong ngày; phần
     * ngày chỉ nói đơn ra đời hôm nào. Số được đệm 0 tới bốn chữ số và <i>không</i> bị cắt khi vượt
     * 9999 — thà mã dài ra còn hơn hai đơn mang cùng một mã.
     *
     * @param nowUtc thời điểm tạo đơn, <b>giờ UTC</b> — cùng mốc ghi vào {@code created_at}
     * @return mã đơn duy nhất tại thời điểm gọi
     */
    String genOrderCode(LocalDateTime nowUtc);

    // ========== LUONG TRANG THAI ==========

    /**
     * <b>Máy trạng thái của đơn hàng — luật này tồn tại ở ĐÚNG MỘT chỗ, và đây là chỗ đó</b>
     * (§B.12.2, khớp {@code src/lib/orderStatus.ts#ORDER_STATUS_TRANSITIONS}).
     * <table border="1">
     *   <caption>Bảng chuyển hợp lệ</caption>
     *   <tr><th>Từ</th><th>Được chuyển sang</th></tr>
     *   <tr><td>{@code pending}</td><td>{@code confirmed}, {@code cancelled}</td></tr>
     *   <tr><td>{@code confirmed}</td><td>{@code shipping}, {@code cancelled}</td></tr>
     *   <tr><td>{@code shipping}</td><td>{@code delivered}, {@code cancelled}</td></tr>
     *   <tr><td>{@code delivered}</td><td>khong con nuoc di nao (trang thai cuoi)</td></tr>
     *   <tr><td>{@code cancelled}</td><td>khong con nuoc di nao (trang thai cuoi)</td></tr>
     * </table>
     * <b>Ba điều dễ làm sai, cả ba đều được hợp đồng nói thẳng:</b>
     * <ul>
     *   <li><b>Chuyển sang CHÍNH trạng thái hiện tại là KHÔNG hợp lệ</b>, không phải một no-op trả
     *       200. Nó không nằm trong danh sách được phép, nên nó là 422. Đây là ca dễ "sửa cho
     *       tiện" nhất của cả endpoint.</li>
     *   <li><b>{@code delivered} và {@code cancelled} có bảng chuyển RỖNG</b> — đó là "không quay
     *       lui được", không phải "chưa liệt kê". Đã giao rồi thì không "chưa xác nhận" lại được;
     *       đã huỷ rồi thì phải tạo đơn mới.</li>
     *   <li><b>Giá trị ngoài dải {@code 0..4} luôn cho {@code false}</b> — không đoán, không rơi về
     *       một nhánh mặc định nào.</li>
     * </ul>
     * <b>Trả {@code boolean} chứ không ném exception</b> (coding-conventions §11 Pattern A) — cùng
     * khuôn với {@link CouponDomainService#isRedeemable}: domain trả lời "được hay không", tầng
     * application dịch {@code false} thành một mã lỗi nghiệp vụ, controller dịch mã đó thành 422.
     * <p>
     * <b>Ô chọn ở giao diện chỉ là tiện tay, không phải hàng rào</b> — chính lớp mock của frontend
     * cũng {@code throw} ở hàm API chứ không chỉ ở component.
     *
     * @param fromStatus trạng thái hiện tại của đơn, con số của cột {@code status}
     * @param toStatus trạng thái muốn chuyển sang, con số của cột {@code status}
     * @return true khi cặp chuyển nằm trong bảng trên; false với mọi cặp còn lại, kể cả cặp trùng
     *         nhau và kể cả con số ngoài dải
     */
    boolean canTransition(int fromStatus, int toStatus);

    // ========== DOC ==========

    /**
     * Tra nhiều sản phẩm <b>còn hiệu lực</b> trong một lượt, đánh chỉ mục theo id.
     * <p>
     * Dùng lại đúng đường đọc mà {@code POST /api/cart/validate} đã dựng ở phase 2
     * ({@code ProductRepository.findByIds}) — không dựng chuỗi song song. Id vắng mặt trong kết quả
     * nghĩa là sản phẩm không tồn tại <i>hoặc</i> đã bị xoá mềm; hai ca đó cố ý không phân biệt
     * được, giống hệt quy ước của giỏ hàng.
     *
     * @param productIds các khoá chính cần tra
     * @return sản phẩm còn hiệu lực theo id; map rỗng khi không khớp dòng nào
     */
    Map<Long, Product> findProductsByIds(Collection<Long> productIds);

    /**
     * Phân giải chủ đơn từ định danh trong token — cùng vai trò mà
     * {@code ProductDomainService.findCategoryById} giữ cho luồng sản phẩm.
     * <p>
     * <b>Tra lại là bắt buộc, không phải phòng thủ thừa.</b> {@code Order.user} là khoá ngoại thật;
     * gắn một entity dựng tay từ con số trong claim {@code sub} sẽ ghi được tham chiếu tới một tài
     * khoản đã bị xoá, và lỗi hiện ra ở tầng ràng buộc chứ không ở chỗ đọc được.
     *
     * @param userId khoá chính lấy từ claim {@code sub}; {@code null} là đơn khách vãng lai
     * @return tài khoản, hoặc {@code null} khi {@code userId} rỗng hoặc không khớp dòng nào
     */
    User findOwnerById(Long userId);

    /**
     * @param code mã đơn
     * @return đơn hàng, hoặc {@code null} khi không có mã nào như vậy
     */
    Order findByCode(String code);

    /**
     * @param userId chủ đơn, lấy từ claim {@code sub} của JWT (§C.4.1)
     * @return các đơn của người dùng này, mới nhất trước; danh sách rỗng khi chưa có đơn nào
     */
    List<Order> findByUserId(Long userId);

    /**
     * Một trang đơn hàng <b>của mọi người dùng</b> — đường đọc của {@code GET /admin/orders}
     * (§B.12.2).
     * <p>
     * <b>Đây là nơi từ khoá {@code q} được bỏ dấu</b>, không phải ở adapter: bỏ dấu là một quy tắc
     * nghiệp vụ (coding-conventions §18), và chuẩn hoá hai lần là hai bản sao của cùng một quy tắc.
     * <p>
     * <b>Dựng một {@link OrderFilter} MỚI thay vì sửa cái được truyền vào</b> — cùng lý do đã viết
     * ở {@code ProductDomainService.findAdminPage}: sửa tại chỗ thì một dòng log ở tầng trên in ra
     * sau lời gọi này sẽ nói sai về chính cái request nó đang xử lý.
     *
     * @param filter điều kiện lọc; {@code keyword} là chuỗi thô client gửi
     * @return trang đơn hàng kèm tổng số dòng khớp điều kiện
     */
    PageResult<Order> findAdminPage(OrderFilter filter);

    /**
     * Dòng hàng của nhiều đơn, gom nhóm theo id đơn — <b>một</b> truy vấn cho cả danh sách.
     *
     * @param orderIds khoá chính của các đơn
     * @return dòng hàng theo id đơn; map rỗng khi đầu vào rỗng
     */
    Map<Long, List<OrderItem>> findItemsGroupedByOrderId(List<Long> orderIds);

    // ========== GHI ==========

    /**
     * Trừ tồn kho bằng <b>conditional UPDATE</b>, không đọc-rồi-ghi (§Contract 8).
     * <p>
     * Điều kiện {@code stock >= :quantity} nằm trong chính câu UPDATE, nên hai người cùng mua món
     * cuối cùng thì đúng một người thắng và người kia nhận {@code false} — không có cửa sổ nào giữa
     * lúc đọc và lúc ghi để lọt qua. Đây là lý do coding-conventions §6 cấm {@code @Version} cho ca
     * này: khoá lạc quan phát hiện xung đột <i>sau khi</i> đã đọc sai, còn UPDATE có điều kiện thì
     * không bao giờ đọc sai.
     *
     * @param productId khoá chính của sản phẩm
     * @param quantity số lượng cần trừ, phải dương
     * @return true khi trừ được đúng một dòng; false khi không đủ tồn kho, sản phẩm không tồn tại,
     *         hoặc sản phẩm đã bị xoá mềm
     */
    boolean deductStock(Long productId, int quantity);

    /**
     * Đốt một lượt của mã giảm giá bằng <b>conditional UPDATE</b>.
     * <p>
     * Điều kiện {@code usedCount < usageLimit} nằm trong câu UPDATE vì cùng một lý do với
     * {@link #deductStock}: {@link CouponDomainService#isRedeemable} đã trả lời "còn lượt" ở một
     * thời điểm <i>trước đó</i>, và giữa hai thời điểm ấy một đơn khác có thể đã lấy mất lượt cuối.
     *
     * @param code mã giảm giá chuẩn trong DB
     * @return true khi đốt được đúng một lượt; false khi mã đã hết lượt hoặc không còn tồn tại
     */
    boolean redeemCoupon(String code);

    /**
     * Ghi đơn hàng.
     *
     * @param draft đơn hàng đã điền đủ trường
     * @return bản ghi sau khi ghi, đã có id
     */
    Order create(Order draft);

    /**
     * Ghi các dòng hàng của một đơn.
     *
     * @param items các dòng hàng, đã gắn sẵn {@code order}
     * @return các bản ghi sau khi ghi, đã có id
     */
    List<OrderItem> createItems(List<OrderItem> items);

    /**
     * Ghi dòng nhật ký <b>đầu tiên</b> của một đơn vừa ra đời (§Contract 9 bước 5).
     * <p>
     * {@code fromStatus} để {@code null} — không phải quên, mà vì đơn không đi <i>từ</i> đâu cả;
     * ghi 0 vào đó sẽ nói rằng đơn đã ở {@code pending} trước khi nó tồn tại.
     *
     * @param order đơn vừa được ghi, đã có id
     * @param changedBy id người dùng dạng chuỗi, hoặc {@link #CHANGED_BY_GUEST}
     * @param createdAt thời điểm ghi, giờ UTC — cùng mốc với {@code created_at} của đơn
     * @return bản ghi nhật ký, đã có id
     */
    OrderStatusHistory recordCreation(Order order, String changedBy, LocalDateTime createdAt);

    /**
     * Ghi trạng thái mới lên đơn.
     * <p>
     * <b>KHÔNG kiểm {@link #canTransition} ở đây, và đó là chủ ý.</b> Method này là <i>một mảnh</i>
     * của transaction do tầng application mở: cổng kiểm chạy trước, dòng nhật ký ghi sau, và cả ba
     * bước phải cùng sống hoặc cùng chết. Nhét phép kiểm vào đây sẽ có hai chỗ cùng cưỡng chế một
     * luật, và chỗ thứ hai là chỗ sẽ bị quên khi luật đổi.
     *
     * @param order đơn đang được transaction quản lý
     * @param toStatus trạng thái mới, con số của cột {@code status}
     * @param nowUtc thời điểm chuyển, <b>giờ UTC</b>; cùng mốc ghi vào dòng nhật ký
     * @return bản ghi sau khi ghi
     */
    Order updateStatus(Order order, int toStatus, LocalDateTime nowUtc);

    /**
     * Ghi một dòng nhật ký <b>chuyển trạng thái</b> (§B.12.2 — mỗi lần chuyển ghi một dòng).
     * <p>
     * Khác {@link #recordCreation}: ở đó {@code fromStatus} để {@code null} vì đơn không đi
     * <i>từ</i> đâu cả; ở đây {@code fromStatus} luôn có giá trị — nó chính là trạng thái đơn vừa
     * rời khỏi, và không có nó thì bảng nhật ký chỉ trả lời được "đơn đã ở đâu", không trả lời được
     * "đơn đi từ đâu tới".
     *
     * @param order đơn vừa chuyển trạng thái
     * @param fromStatus trạng thái trước khi chuyển
     * @param toStatus trạng thái sau khi chuyển
     * @param changedBy định danh admin thực hiện, lấy từ claim {@code sub}
     * @param createdAt thời điểm chuyển, giờ UTC — cùng mốc với {@code updated_at} của đơn
     * @return bản ghi nhật ký, đã có id
     */
    OrderStatusHistory recordTransition(Order order, Integer fromStatus, int toStatus,
                                        String changedBy, LocalDateTime createdAt);

    // ========== TONG HOP (§B.12.4) ==========

    /**
     * Đúng {@code days} ngày liên tiếp <b>theo giờ cửa hàng</b>, tăng dần, kết thúc ở <b>hôm
     * nay</b>.
     * <p>
     * Đây là bộ khung zero-fill của {@code revenueByDay} và cũng là thứ định nghĩa khoảng thời gian
     * của cả bốn số phụ thuộc {@code days}. Khớp {@code buildDateWindow} của frontend
     * ({@code adminStats.api.ts:59-69}): hôm nay <b>nằm trong</b> khoảng, nên {@code days=7} là
     * "hôm nay và sáu ngày trước", không phải "bảy ngày trước hôm nay".
     *
     * @param days số ngày, đã được tầng trên kiểm nằm trong dải hợp lệ
     * @return danh sách ngày tăng dần, đúng {@code days} phần tử
     */
    List<LocalDate> genDateWindow(int days);

    /**
     * Doanh thu gom theo ngày cửa hàng trong khoảng {@code [fromDate, toDate]}.
     * <p>
     * <b>Đây là nơi ngày cửa hàng được đổi thành mốc UTC</b> — {@link #STORE_ZONE} không đi xuống
     * dưới domain. Kết quả <b>thưa</b>: chỉ những ngày có đơn.
     *
     * @param fromDate ngày đầu khoảng, giờ cửa hàng, đã bao gồm
     * @param toDate ngày cuối khoảng, giờ cửa hàng, đã bao gồm
     * @return doanh thu theo ngày, tăng dần
     */
    List<DailyRevenue> findRevenueByDay(LocalDate fromDate, LocalDate toDate);

    /**
     * Số đơn gom theo trạng thái trong <b>cùng</b> khoảng với {@link #findRevenueByDay}.
     * <p>
     * Kết quả <b>thưa</b>: chỉ những trạng thái có đơn.
     *
     * @param fromDate ngày đầu khoảng, giờ cửa hàng, đã bao gồm
     * @param toDate ngày cuối khoảng, giờ cửa hàng, đã bao gồm
     * @return số đơn theo trạng thái
     */
    List<StatusCount> countOrdersByStatus(LocalDate fromDate, LocalDate toDate);
}
