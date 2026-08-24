package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.CouponResponse;
import com.nss.ddd.application.model.response.CouponValidationResponse;
import com.nss.ddd.application.service.coupon.CouponAppService;
import com.nss.ddd.controller.dto.ValidateCouponRequest;
import com.nss.ddd.controller.exception.CouponNotApplicableException;
import com.nss.ddd.controller.exception.CouponNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Biên REST của mã giảm giá — API_CONTRACT §B.7.
 * <p>
 * Trả DTO trần, <b>không bọc {@code ResultMessage}</b> (ADR 0001, giống {@code ProductController});
 * thất bại dùng mã HTTP thật và {@code ProblemDetail} do {@code GlobalExceptionHandler} dựng.
 * <p>
 * <b>Cả hai endpoint công khai.</b> Giỏ hàng sống trong localStorage của khách vãng lai và mã giảm
 * giá phải áp được trước khi đăng nhập, nên §B.7 đánh dấu cả hai là ⬜. Đó không phải lựa chọn của
 * file này mà của {@code SecurityConfig} — nơi duy nhất quyết định endpoint nào cần quyền gì; hai
 * method bên dưới cố ý <b>không</b> khai {@code @SecurityRequirement} để Swagger nói đúng sự thật đó.
 * <p>
 * <b>Không có đường ghi nào ở đây, và đó là một ràng buộc chứ không phải chỗ còn thiếu.</b>
 * {@code POST /coupons/validate} là {@code POST} vì nó mang body, không phải vì nó thay đổi trạng
 * thái: nó <i>không</i> tăng {@code usedCount}. Frontend gọi lại nó mỗi lần giá trị đơn thay đổi
 * (§B.7 — giỏ nằm trong localStorage nhiều ngày, áp mã lúc đơn 300k rồi xoá bớt hàng còn 100k thì
 * mã phải hết hiệu lực ngay), nên một lần tăng lượt ở đây sẽ đốt sạch chiến dịch bằng đúng thao tác
 * thêm bớt hàng trong giỏ. Việc tăng lượt thuộc phase 3, trong cùng transaction với INSERT đơn.
 * <p>
 * Mọi {@code @RequestParam} / {@code @PathVariable} phải <b>khai tên tường minh</b> vì dự án không
 * bật cờ {@code -parameters} (xem javadoc {@code ProductController}). Hai method dưới đây không có
 * tham số nào như vậy, nhưng luật vẫn áp dụng khi thêm ca mới.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Mã giảm giá",
        description = "Xác thực mã giảm giá và liệt kê các mã đang chạy. Cả hai đều công khai.")
public class CouponController {

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    private final CouponAppService couponAppService;

    /**
     * @param request body đã qua validate
     * @return mã giảm giá hợp lệ cho giá trị đơn đã gửi
     * @throws CouponNotFoundException khi không có mã nào như vậy
     * @throws CouponNotApplicableException khi mã có thật nhưng không dùng được cho đơn này
     */
    @Operation(summary = "Xác thực một mã giảm giá cho giá trị đơn cụ thể",
            description = """
                    Kiểm một mã giảm giá và trả về mã đó khi dùng được cho `subtotal` đã gửi.

                    - **Không phân biệt hoa thường và tự cắt khoảng trắng hai đầu** — gửi \
                    `"  huuco50  "` vẫn ra mã `HUUCO50`. `code` trong response luôn là **mã chuẩn \
                    trong DB**, không phải chuỗi người dùng gõ.
                    - `type` là chuỗi thường: `percent` hoặc `fixed`.
                    - `value` là **số phần trăm** khi `type = percent` (giá trị `10` nghĩa là 10%), \
                    là **số nguyên VNĐ** khi `type = fixed`.
                    - `subtotal` và `minOrderValue` là **số nguyên VNĐ**, không thập phân.

                    **Endpoint này chỉ đọc.** Nó không tính vào số lượt đã dùng của mã — frontend \
                    gọi lại mỗi khi giá trị đơn thay đổi, nên tăng lượt ở đây sẽ đốt sạch chiến \
                    dịch. Lượt chỉ tăng khi đơn hàng thật sự được tạo.""")
    @ApiResponse(responseCode = "200", description = "Mã hợp lệ và dùng được cho giá trị đơn đã gửi")
    @ApiResponse(responseCode = "404",
            description = "Không có mã giảm giá nào mang chuỗi này; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Hai nhóm lý do, cùng trả `422` và `detail` viết tiếng Việt:

                    - **Body không hợp lệ** (thiếu `code`, `subtotal` âm) — kèm phần mở rộng \
                    `errors` map `tên trường → thông điệp`.
                    - **Mã không dùng được cho đơn này** — `subtotal` chưa đạt `minOrderValue` \
                    (`detail` nêu rõ con số tối thiểu), hoặc mã đã tắt / ngoài cửa sổ hiệu lực / \
                    hết lượt.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/coupons/validate")
    public CouponResponse validateCoupon(@Valid @RequestBody ValidateCouponRequest request) {
        log.info("CouponController:->validateCoupon | code={} subtotal={}",
                request.getCode(), request.getSubtotal());
        return extractOrThrow(couponAppService.validateCoupon(request.getCode(), request.getSubtotal()));
    }

    /**
     * @return các mã đang chạy; mảng rỗng khi không có mã nào
     */
    @Operation(summary = "Danh sách mã giảm giá đang chạy",
            description = """
                    Trả về các mã **đang có hiệu lực ngay lúc gọi**: đang bật, còn trong cửa sổ \
                    thời gian, và chưa hết lượt sử dụng.

                    **Không phân trang** — hợp đồng trả về mảng trần. Danh sách sắp theo `code` \
                    tăng dần để thứ tự ổn định giữa các lần gọi.

                    Mã đã tắt, hết hạn, chưa tới ngày bắt đầu hoặc đã hết lượt **không xuất hiện** \
                    ở đây; gọi `POST /api/coupons/validate` với chúng sẽ nhận `422`.""")
    @ApiResponse(responseCode = "200", description = "Các mã đang chạy; mảng rỗng khi không có mã nào")
    @GetMapping("/coupons/active")
    public List<CouponResponse> getActiveCoupons() {
        log.info("CouponController:->getActiveCoupons");
        return couponAppService.findActiveCoupons();
    }

    /**
     * Dịch kết quả của tầng application thành payload hoặc exception.
     * <p>
     * Đây là chỗ duy nhất mã lỗi nghiệp vụ gặp mã HTTP: application không được biết HTTP, và kiểu
     * {@code *Exception} sống ở module controller (§3) nên application cũng không ném được chúng.
     * <p>
     * Nhánh cuối rơi về <b>422</b> chứ không phải 404: một mã lỗi mới ra đời mà quên khai ở đây thì
     * "không xử lý được yêu cầu" là câu trả lời an toàn, còn "không tìm thấy" là một lời khẳng định
     * sai về dữ liệu.
     *
     * @param result kết quả xác thực
     * @return mã giảm giá khi hợp lệ
     */
    private CouponResponse extractOrThrow(CouponValidationResponse result) {
        if (result.getCoupon() != null) {
            return result.getCoupon();
        }
        if (CouponValidationResponse.CODE_COUPON_NOT_FOUND.equals(result.getCode())) {
            throw new CouponNotFoundException(result.getMessage());
        }
        throw new CouponNotApplicableException(result.getMessage());
    }
}
