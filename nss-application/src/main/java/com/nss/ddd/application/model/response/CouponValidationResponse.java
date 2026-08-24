package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Kết quả của {@code POST /coupons/validate} — thành công thì mang {@code coupon}, thất bại thì
 * mang {@code code} và {@code message}.
 * <p>
 * Cùng khuôn với {@link ProductMutationResponse} và cùng lý do: coding-conventions §11 Pattern A
 * nói thất bại nghiệp vụ là <b>giá trị trả về</b>, và §3 đặt mọi kiểu {@code *Exception} ở module
 * <i>controller</i> — mà application nằm <i>dưới</i> controller trong chiều phụ thuộc nên không thể
 * ném chúng. Controller là nơi dịch {@code code} thành mã HTTP thật.
 * <p>
 * Đối tượng này <b>không bao giờ đi ra dây</b>: controller lấy {@code coupon} ra trả trần, hoặc ném
 * exception tương ứng. {@code message} viết <b>tiếng Việt</b> vì nó chính là {@code detail} của
 * {@code ProblemDetail} mà frontend hiển thị thẳng cho người dùng cuối (§A.3).
 * <p>
 * <b>Hai mã lỗi, không phải một</b> — và sự khác nhau giữa chúng là 404 với 422:
 * <ul>
 *   <li>{@link #CODE_COUPON_NOT_FOUND} → <b>404</b>: không có mã nào như vậy.</li>
 *   <li>{@link #CODE_COUPON_NOT_APPLICABLE} → <b>422</b>: mã có thật nhưng dùng không được — đã
 *       tắt, ngoài cửa sổ hiệu lực, hết lượt, hoặc đơn chưa đạt giá trị tối thiểu.</li>
 * </ul>
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidationResponse {

    /** Mã không tồn tại trong bảng {@code coupon} — controller dịch thành <b>404</b>. */
    public static final String CODE_COUPON_NOT_FOUND = "COUPON_NOT_FOUND";

    /**
     * Mã tồn tại nhưng không dùng được cho đơn này — controller dịch thành <b>422</b>.
     * <p>
     * Gộp bốn tình huống vào một mã là có chủ ý: consumer duy nhất là frontend, và nó chỉ hiển thị
     * chuỗi {@code detail} chứ không phân nhánh theo mã lỗi. Sự khác nhau giữa "hết hạn" và "chưa
     * đủ giá trị tối thiểu" đã nằm trong {@code message} — chỗ người dùng thật sự đọc.
     */
    public static final String CODE_COUPON_NOT_APPLICABLE = "COUPON_NOT_APPLICABLE";

    /** Mã giảm giá hợp lệ; {@code null} khi thất bại. */
    private CouponResponse coupon;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; {@code null} khi thành công. */
    private String code;

    /** Thông điệp tiếng Việt cho người dùng cuối; {@code null} khi thành công. */
    private String message;

    /**
     * @param coupon mã giảm giá hợp lệ
     * @return kết quả thành công
     */
    public static CouponValidationResponse success(CouponResponse coupon) {
        return new CouponValidationResponse().setCoupon(coupon);
    }

    /**
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    public static CouponValidationResponse failed(String code, String message) {
        return new CouponValidationResponse()
                .setCode(code)
                .setMessage(message);
    }
}
