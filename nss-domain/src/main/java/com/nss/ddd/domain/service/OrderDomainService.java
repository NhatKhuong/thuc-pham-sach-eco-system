package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.OrderStatusHistory;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.User;

import java.time.LocalDateTime;
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
}
