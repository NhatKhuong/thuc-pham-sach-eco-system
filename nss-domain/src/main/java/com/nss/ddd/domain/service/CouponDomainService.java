package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.entity.Coupon;

import java.util.List;

/**
 * Domain service của aggregate {@code Coupon} — nơi ở của quy tắc "mã này có dùng được không".
 * <p>
 * <b>Thất bại nghiệp vụ ở đây là giá trị trả về, không phải exception</b>
 * (coding-conventions §11 Pattern A): {@code null} cho "không tồn tại", {@code false} cho "không
 * dùng được". Việc dịch chúng thành mã HTTP là của tầng controller — kiểu {@code *Exception} sống
 * ở module controller (§3) nên domain không thể, và không nên, ném chúng.
 * <p>
 * <b>Phase 3 sẽ gọi lại chính hai vị từ dưới đây</b> khi tạo đơn (backlog 0014 §Contract 9 bước 3).
 * Đó là lý do quy tắc nằm ở domain chứ không nằm trong controller: cưỡng chế hai lần bằng hai đoạn
 * code khác nhau thì sớm muộn chúng lệch nhau, và triệu chứng là một đơn được giảm giá bằng mã mà
 * {@code POST /coupons/validate} vừa từ chối.
 */
public interface CouponDomainService {

    /**
     * Kiểu giảm giá theo phần trăm — cột {@code coupon.type} lưu {@code 0}, dây mang chuỗi
     * {@code percent} (backlog 0014 §Contract 4).
     * <p>
     * <b>Hai hằng này khai ở domain vì có hai người dùng ở hai tầng khác nhau.</b>
     * {@code OrderDomainServiceImpl.calcDiscount} đọc chúng để biết nhân phần trăm hay lấy số tiền
     * trần, còn {@code CouponMapper} đọc chúng để dịch sang chuỗi của dây. Chiều phụ thuộc chỉ cho
     * phép một hướng — application thấy domain, domain không thấy application — nên chỗ duy nhất
     * chứa được cả hai người dùng là domain. Khai hai bản thì một ngày nào đó phép tính coi
     * {@code 0} là {@code fixed} trong khi bảng dịch vẫn nói {@code percent}, và triệu chứng là một
     * đơn giảm 10 ₫ thay vì 10%.
     */
    int TYPE_PERCENT = 0;

    /** Kiểu giảm giá theo số tiền cố định — cột lưu {@code 1}, dây mang {@code fixed}. */
    int TYPE_FIXED = 1;

    /**
     * Tra mã theo chuỗi người dùng gõ.
     * <p>
     * <b>Cắt khoảng trắng hai đầu và bỏ qua hoa thường</b> (backlog 0014 §Contract 4): frontend gửi
     * thẳng thứ người dùng gõ, nên {@code "  huuco50  "} phải tìm ra {@code HUUCO50}. Mã trả về là
     * bản ghi <b>nguyên trạng trong DB</b> — chuỗi người dùng gõ không bao giờ được đi ngược ra
     * response.
     *
     * @param rawCode chuỗi người dùng gõ, có thể thừa khoảng trắng và sai hoa thường
     * @return mã giảm giá, hoặc {@code null} khi {@code rawCode} rỗng / chỉ có khoảng trắng /
     *         không khớp dòng nào
     */
    Coupon findByCode(String rawCode);

    /**
     * Mã có đang ở trạng thái dùng được không — <b>độc lập với giá trị đơn hàng</b>.
     * <p>
     * Ba điều kiện, tất cả phải đúng:
     * <ul>
     *   <li>{@code isActive = true} — cờ bật/tắt thủ công;</li>
     *   <li>trong cửa sổ {@code startsAt}–{@code endsAt}, so theo <b>giờ UTC</b>; đầu nào
     *       {@code null} thì đầu đó không giới hạn;</li>
     *   <li>{@code usedCount < usageLimit}; {@code usageLimit} {@code null} nghĩa là không giới hạn.</li>
     * </ul>
     *
     * @param coupon mã cần kiểm; {@code null} coi như không dùng được
     * @return true nếu mã đang chạy
     */
    boolean isRedeemable(Coupon coupon);

    /**
     * Giá trị đơn đã đạt ngưỡng {@code minOrderValue} của mã chưa.
     * <p>
     * Tách khỏi {@link #isRedeemable} vì hai câu trả lời "không" này khác nhau với người dùng: một
     * cái nói "mã hỏng rồi", cái kia nói "mua thêm là dùng được" — và §A.3 bắt {@code detail} phải
     * nói được <i>sai ở đâu</i>.
     *
     * @param coupon mã cần kiểm; {@code null} coi như không đạt
     * @param subtotal tổng tiền hàng, số nguyên VNĐ (§A.5); {@code null} coi như 0
     * @return true nếu {@code subtotal >= minOrderValue}
     */
    boolean meetsMinOrderValue(Coupon coupon, Long subtotal);

    /**
     * Các mã đang chạy tại thời điểm hiện tại — đầu vào của {@code GET /coupons/active}.
     * <p>
     * Cùng ba điều kiện với {@link #isRedeemable}, nhưng lọc ở tầng SQL thay vì lọc trong Java:
     * bảng mã giảm giá là bảng dùng chung của mọi khách, kéo hết về rồi lọc là thứ hỏng dần theo
     * thời gian mà không có mốc nào báo.
     *
     * @return danh sách mã còn hiệu lực, thứ tự ổn định; danh sách rỗng khi không có mã nào
     */
    List<Coupon> findRedeemableCoupons();
}
