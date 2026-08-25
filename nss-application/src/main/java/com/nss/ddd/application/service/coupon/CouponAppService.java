package com.nss.ddd.application.service.coupon;

import com.nss.ddd.application.model.response.CouponResponse;
import com.nss.ddd.application.model.response.CouponValidationResponse;

import java.util.List;

/**
 * Use case mã giảm giá — API_CONTRACT §B.7.
 * <p>
 * Không có quy tắc nghiệp vụ nào ở đây: quy tắc sống trong {@code CouponDomainService}, tầng này
 * chỉ hỏi domain rồi lắp kết quả thành response kèm thông điệp tiếng Việt.
 * <p>
 * <b>Cả hai đường đều CHỈ ĐỌC.</b> Xác thực một mã không được phép động vào {@code usedCount} —
 * frontend gọi lại endpoint này <i>mỗi lần giá trị đơn thay đổi</i> (§B.7), nên tăng lượt ở đây sẽ
 * đốt sạch một chiến dịch chỉ bằng việc người dùng thêm bớt hàng trong giỏ. Việc tăng
 * {@code usedCount} thuộc phase 3, trong cùng transaction với INSERT đơn.
 */
public interface CouponAppService {

    /**
     * Xác thực một mã cho một giá trị đơn cụ thể.
     *
     * @param rawCode chuỗi mã người dùng gõ — có thể thừa khoảng trắng và sai hoa thường
     * @param subtotal tổng tiền hàng, số nguyên VNĐ (§A.5)
     * @return kết quả mang mã hợp lệ, hoặc mã lỗi nghiệp vụ kèm thông điệp tiếng Việt
     */
    CouponValidationResponse validateCoupon(String rawCode, Long subtotal);

    /**
     * Các mã đang chạy — dùng cho màn hình gợi ý mã ở giỏ hàng.
     * <p>
     * <b>Không phân trang</b>: hợp đồng §B.7 trả {@code Coupon[]} trần.
     *
     * @return danh sách mã còn hiệu lực; danh sách rỗng khi không có mã nào
     */
    List<CouponResponse> findActiveCoupons();
}
