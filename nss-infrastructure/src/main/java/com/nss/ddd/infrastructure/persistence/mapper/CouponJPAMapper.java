package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data interface của {@code coupon} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * Khóa chính là {@code code} kiểu {@code String} (natural key, ticket 0004), nên tham số kiểu của
 * {@code JpaRepository} là {@code <Coupon, String>} chứ không phải {@code <Coupon, Long>}.
 * <p>
 * Không có quan hệ nào để {@code JOIN FETCH}: {@code Coupon} là bảng phẳng, mọi trường đều là kiểu
 * cơ bản. Đó là lý do file này ngắn hơn hẳn {@link ProductJPAMapper} dù cùng vai trò.
 */
public interface CouponJPAMapper extends JpaRepository<Coupon, String> {

    /**
     * Tra mã <b>không phân biệt hoa thường</b>.
     * <p>
     * <b>Vì sao {@code UPPER()} tường minh thay vì trông vào collation.</b> Cột {@code code} hiện
     * dùng {@code utf8mb4_unicode_ci} — hậu tố {@code _ci} nghĩa là MySQL đã so sánh bỏ qua hoa
     * thường, nên {@code findById("huuco50")} <i>cũng</i> ra kết quả đúng trên schema hôm nay. Đúng
     * vì lý do sai: nó biến một điều khoản của contract (§Contract 4) thành hệ quả phụ của một
     * thuộc tính schema mà không dòng code nào nhắc tới. Một lần đổi collation sang {@code _bin} —
     * hoặc một lần chuyển sang engine khác — sẽ làm mã người dùng gõ thường không tra ra gì nữa, và
     * triệu chứng là "404 mã không tồn tại" chứ không phải một lỗi cấu hình đọc được.
     * <p>
     * Cái giá là câu truy vấn không dùng được index khóa chính. Chấp nhận được: bảng này có 3 dòng
     * ở seed và bản chất là bảng cấu hình chứ không phải bảng giao dịch.
     *
     * @param code mã cần tra, đã cắt khoảng trắng; hoa thường tuỳ ý
     * @return mã giảm giá nguyên trạng trong DB, hoặc rỗng
     */
    @Query("SELECT c FROM Coupon c WHERE UPPER(c.code) = UPPER(:code)")
    Optional<Coupon> findByCodeIgnoreCase(@Param("code") String code);

    /**
     * Các mã đang chạy tại thời điểm {@code now}.
     * <p>
     * Ba điều kiện hiệu lực nằm <b>trong câu truy vấn</b>, không lọc lại phía Java. {@code null}
     * ở {@code startsAt} / {@code endsAt} / {@code usageLimit} nghĩa là "không giới hạn phía đó",
     * nên mỗi vế đều phải có nhánh {@code IS NULL} — thiếu nó thì cả ba mã seed (đều để
     * {@code NULL} cả ba cột) biến mất khỏi kết quả và endpoint trả mảng rỗng mà không báo lỗi gì.
     * <p>
     * {@code ORDER BY c.code} để thứ tự <b>ổn định</b> giữa các lần gọi: không có nó, MySQL được
     * phép đổi thứ tự và test đếm/so khớp sẽ đỏ ngẫu nhiên.
     *
     * @param now thời điểm so sánh, giờ UTC
     * @return danh sách mã còn hiệu lực
     */
    @Query("SELECT c FROM Coupon c"
            + " WHERE c.isActive = true"
            + " AND (c.startsAt IS NULL OR c.startsAt <= :now)"
            + " AND (c.endsAt IS NULL OR c.endsAt >= :now)"
            + " AND (c.usageLimit IS NULL OR c.usedCount < c.usageLimit)"
            + " ORDER BY c.code ASC")
    List<Coupon> findRedeemable(@Param("now") LocalDateTime now);

    /**
     * Đốt một lượt của mã bằng <b>conditional UPDATE</b> (backlog 0014 §Contract 8).
     * <p>
     * <b>Vế {@code usedCount < usageLimit} nằm trong chính câu UPDATE</b>, cùng kỷ luật với
     * {@code ProductJPAMapper.decreaseStock} và cùng lý do: {@code CouponDomainService.isRedeemable}
     * đã trả lời "còn lượt" ở một thời điểm trước đó trong cùng transaction, nhưng một đơn khác có
     * thể đã lấy mất lượt cuối trong khoảng giữa. Một chiến dịch giới hạn 100 lượt mà kiểm-rồi-ghi
     * sẽ phát ra 103 mã giảm giá vào giờ cao điểm, và không có gì báo lỗi.
     * <p>
     * {@code usageLimit} {@code null} nghĩa là không giới hạn nên phải có nhánh {@code IS NULL} —
     * thiếu nó thì cả ba mã seed (đều để {@code NULL}) không đốt được lượt nào và <b>mọi</b> đơn có
     * mã giảm giá rơi xuống 422.
     * <p>
     * So khớp {@code UPPER()} giữ đúng quy ước của {@link #findByCodeIgnoreCase} — nhưng tầng gọi
     * luôn truyền vào mã <i>chuẩn trong DB</i> lấy từ chính bản ghi vừa tra, không truyền chuỗi
     * người dùng gõ.
     *
     * @param code mã giảm giá
     * @return số dòng bị ảnh hưởng — {@code 1} là đốt được một lượt, {@code 0} là đã hết lượt hoặc
     *         mã không tồn tại
     */
    @Modifying
    @Transactional
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount + 1"
            + " WHERE UPPER(c.code) = UPPER(:code)"
            + " AND (c.usageLimit IS NULL OR c.usedCount < c.usageLimit)")
    int increaseUsedCount(@Param("code") String code);
}
