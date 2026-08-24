package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.Coupon;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PORT của aggregate {@code Coupon} — domain khai báo, infrastructure implement.
 * <p>
 * <b>Ràng buộc kiến trúc:</b> file này không được import bất cứ thứ gì thuộc
 * {@code org.springframework.data.*}, giống {@link ProductRepository}. Mất ranh giới này là mất lý
 * do chia module (architecture/01-overview.md §1).
 * <p>
 * <b>Không có đường ghi nào ở đây.</b> Phase 1 của backlog 0014 chỉ đọc; việc tăng
 * {@code usedCount} thuộc phase 3 vì nó phải nằm trong cùng transaction với INSERT đơn hàng —
 * tăng ở một đường đọc thì mỗi lần người dùng gõ lại mã trong giỏ sẽ đốt mất một lượt.
 */
public interface CouponRepository {

    /**
     * Tra mã giảm giá theo {@code code}, <b>không phân biệt hoa thường</b>.
     * <p>
     * Frontend gửi thẳng chuỗi người dùng gõ ({@code coupons.api.ts} hạ về chữ thường trước khi
     * gửi), nên {@code "huuco50"} phải tìm ra dòng {@code HUUCO50}. Việc cắt khoảng trắng hai đầu
     * là của tầng gọi — port này nhận chuỗi đã sạch.
     *
     * @param code mã cần tra, đã cắt khoảng trắng; hoa thường tuỳ ý
     * @return mã giảm giá <b>nguyên trạng trong DB</b> (kể cả đã tắt / hết hạn / hết lượt), hoặc
     *         rỗng khi không có dòng nào khớp
     */
    Optional<Coupon> findByCode(String code);

    /**
     * Các mã <b>đang chạy</b> tại thời điểm {@code now} — đầu vào của {@code GET /coupons/active}.
     * <p>
     * "Đang chạy" gồm cả ba điều kiện: {@code isActive = true}, còn trong cửa sổ
     * {@code startsAt}–{@code endsAt}, và chưa chạm {@code usageLimit}. Ba cột đó đã có trong
     * schema từ ticket 0004; không cưỡng chế thì chúng là lời hứa suông (backlog 0014 §Contract 8).
     *
     * @param now thời điểm so sánh, <b>giờ UTC</b> — cột {@code starts_at} / {@code ends_at} lưu UTC
     * @return danh sách mã còn hiệu lực, sắp theo {@code code} tăng dần cho thứ tự ổn định;
     *         danh sách rỗng khi không có mã nào
     */
    List<Coupon> findRedeemable(LocalDateTime now);
}
