package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload của một mã giảm giá trên bề mặt dây — khớp <b>đúng</b> type {@code Coupon} của frontend
 * (API_CONTRACT §B.7, {@code src/types/cart.ts}).
 * <p>
 * DTO trần, không bọc {@code ResultMessage} — ADR 0001.
 * <p>
 * <b>Đúng năm trường, và con số đó là contract chứ không phải hiện trạng.</b> Bảng {@code coupon}
 * còn {@code isActive}, {@code startsAt}, {@code endsAt}, {@code usageLimit}, {@code usedCount},
 * {@code createdAt}, {@code updatedAt} — <b>không trường nào trong số đó được lên dây</b>. Chúng là
 * dữ liệu vận hành nội bộ: {@code usedCount} nói cho bất kỳ ai gọi endpoint công khai này biết một
 * chiến dịch đã tiêu bao nhiêu lượt, và {@code endsAt} nói mã sắp hết hạn lúc nào. Thêm một trường
 * vào đây là một thay đổi contract, không phải tiện tay.
 * <p>
 * Đây cũng là lý do endpoint trả <b>DTO chứ không trả entity</b>: trả thẳng {@code Coupon} thì cả
 * bảy cột nội bộ đi ra ngoài, và không có gì báo lỗi.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CouponResponse {

    /** Mã <b>chuẩn trong DB</b>, không phải chuỗi người dùng gõ (backlog 0014 §Contract 4). */
    private String code;

    /**
     * {@code "percent"} hoặc {@code "fixed"} — <b>chuỗi thường</b> trên dây, {@code int} trong DB.
     * <p>
     * Bảng dịch nằm đúng một chỗ: {@code CouponMapper}. Xem javadoc ở đó.
     */
    private String type;

    /**
     * Số phần trăm khi {@code type = "percent"} (giá trị {@code 10} nghĩa là 10%), số nguyên VNĐ
     * khi {@code type = "fixed"} (§A.5).
     * <p>
     * <b>Không phải {@code 0.1} và không phải chuỗi.</b> Frontend nhân thẳng vào {@code subtotal}
     * rồi chia 100; một giá trị đã chia sẵn sẽ làm mọi đơn giảm 0,1% thay vì 10%, và con số vẫn
     * trông hợp lý nên không ai nhìn ra ngay.
     */
    private Long value;

    /** Giá trị đơn tối thiểu để dùng mã, số nguyên VNĐ (§A.5). */
    private Long minOrderValue;

    /** Chuỗi mô tả hiển thị cho người dùng. */
    private String description;
}
