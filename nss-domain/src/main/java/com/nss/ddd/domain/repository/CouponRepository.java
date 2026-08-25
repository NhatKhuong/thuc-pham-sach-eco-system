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
 * <b>Đúng một đường ghi, và nó chỉ được gọi từ luồng tạo đơn.</b> Phase 1 của backlog 0014 cố ý
 * không có đường ghi nào; {@link #increaseUsedCount} ra đời ở phase 3 vì nó phải nằm trong cùng
 * transaction với INSERT đơn hàng. Gọi nó từ một đường <i>đọc</i> — {@code POST /coupons/validate}
 * chẳng hạn — sẽ đốt một lượt mỗi lần người dùng thêm bớt hàng trong giỏ, tức đốt sạch chiến dịch
 * bằng đúng thao tác bình thường nhất của khách.
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

    /**
     * Đốt một lượt của mã bằng <b>conditional UPDATE</b> — {@code usedCount = usedCount + 1} với
     * điều kiện chưa chạm {@code usageLimit} (backlog 0014 §Contract 8).
     * <p>
     * <b>Điều kiện nằm trong câu UPDATE chứ không kiểm trước rồi ghi sau.</b>
     * {@code CouponDomainService.isRedeemable} đã trả lời "còn lượt" ở một thời điểm <i>trước đó</i>
     * trong cùng transaction, nhưng một đơn khác có thể đã lấy mất lượt cuối trong khoảng giữa. Đây
     * là cùng một kỷ luật với {@code ProductRepository.decreaseStock} và cùng lý do
     * coding-conventions §6 cấm {@code @Version}.
     * <p>
     * {@code usageLimit} {@code null} nghĩa là không giới hạn, nên vế điều kiện phải có nhánh
     * {@code IS NULL} — thiếu nó thì cả ba mã seed (đều để {@code NULL}) không bao giờ đốt được
     * lượt nào và mọi đơn có mã đều rơi xuống 422.
     *
     * @param code mã giảm giá chuẩn trong DB
     * @return true khi có đúng một dòng tăng lượt; false khi mã đã hết lượt hoặc không tồn tại
     */
    boolean increaseUsedCount(String code);
}
