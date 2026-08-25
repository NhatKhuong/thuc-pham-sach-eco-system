package com.nss.ddd.application.service.coupon.impl;

import com.nss.ddd.application.mapper.CouponMapper;
import com.nss.ddd.application.model.response.CouponResponse;
import com.nss.ddd.application.model.response.CouponValidationResponse;
import com.nss.ddd.application.service.coupon.CouponAppService;
import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.service.CouponDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;

/**
 * Hiện thực use case mã giảm giá.
 * <p>
 * Tầng này chỉ điều phối: hỏi domain service, rồi lắp kết quả thành kiểu của bề mặt dây. Không có
 * quy tắc nghiệp vụ nào nằm ở đây — cái duy nhất thuộc về tầng này là <b>chuỗi tiếng Việt</b> mà
 * người dùng cuối đọc, vì nó là chuyện trình bày chứ không phải chuyện nghiệp vụ.
 * <p>
 * <b>Không {@code @Transactional}:</b> cả hai đường đều chỉ đọc và mỗi đường chỉ một truy vấn duy
 * nhất, nên không có gì để gói lại. coding-conventions §8 mục 5 cấm khai {@code readOnly} khi không
 * viết ra được lý do — ở đây không có lý do nào.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponAppServiceImpl implements CouponAppService {

    private static final String MESSAGE_COUPON_NOT_FOUND =
            "Mã giảm giá không tồn tại, vui lòng kiểm tra lại.";

    private static final String MESSAGE_COUPON_NOT_REDEEMABLE =
            "Mã giảm giá này không còn hiệu lực.";

    private final CouponDomainService couponDomainService;

    @Override
    public CouponValidationResponse validateCoupon(String rawCode, Long subtotal) {
        // 1. Tra ma — domain cat khoang trang va bo qua hoa thuong (§Contract 4)
        Coupon coupon = couponDomainService.findByCode(rawCode);
        if (coupon == null) {
            log.warn("validateCoupon: not found | code={}", rawCode);
            return CouponValidationResponse.failed(
                    CouponValidationResponse.CODE_COUPON_NOT_FOUND, MESSAGE_COUPON_NOT_FOUND);
        }
        // 2. Trang thai cua chinh ma — kiem TRUOC nguong gia tri: mot ma da het han thi cau
        //    "mua them di" la loi khuyen sai, mua bao nhieu cung khong dung duoc.
        if (!couponDomainService.isRedeemable(coupon)) {
            log.warn("validateCoupon: not redeemable | code={}", coupon.getCode());
            return CouponValidationResponse.failed(
                    CouponValidationResponse.CODE_COUPON_NOT_APPLICABLE, MESSAGE_COUPON_NOT_REDEEMABLE);
        }
        // 3. Nguong gia tri don — thong diep PHAI neu con so, khong thi nguoi dung khong biet
        //    thieu bao nhieu (§A.3: `detail` phai noi duoc SAI O DAU)
        if (!couponDomainService.meetsMinOrderValue(coupon, subtotal)) {
            log.warn("validateCoupon: subtotal below minimum | code={} subtotal={} minOrderValue={}",
                    coupon.getCode(), subtotal, coupon.getMinOrderValue());
            return CouponValidationResponse.failed(CouponValidationResponse.CODE_COUPON_NOT_APPLICABLE,
                    genMinOrderValueMessage(coupon.getMinOrderValue()));
        }
        log.info("validateCoupon: success | code={} subtotal={}", coupon.getCode(), subtotal);
        return CouponValidationResponse.success(CouponMapper.toResponse(coupon));
    }

    @Override
    public List<CouponResponse> findActiveCoupons() {
        List<Coupon> coupons = couponDomainService.findRedeemableCoupons();
        log.info("findActiveCoupons: success | count={}", coupons.size());
        return CouponMapper.toResponses(coupons);
    }

    // ========== HELPERS ==========

    /**
     * Dựng thông điệp tiếng Việt cho lỗi chưa đạt giá trị đơn tối thiểu.
     * <p>
     * Chuỗi này đi thẳng vào {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên
     * văn (§A.3) — khuôn câu lấy đúng ví dụ trong tài liệu đó.
     * <p>
     * <b>Ngăn cách hàng nghìn khai tường minh là dấu chấm, không mượn locale mặc định của JVM.</b>
     * {@code DecimalFormat} không tham số đọc {@code Locale.getDefault()}: máy dev Việt Nam cho ra
     * {@code 500.000}, một JVM chạy locale {@code en-US} cho ra {@code 500,000}, và một JVM
     * {@code fr-FR} cho ra {@code 500 000} với dấu cách không ngắt. Cùng một dòng code, ba chuỗi
     * khác nhau tuỳ máy — và không có gì báo lỗi, chỉ có người dùng đọc một con số lạ.
     *
     * @param minOrderValue giá trị đơn tối thiểu, số nguyên VNĐ
     * @return thông điệp tiếng Việt có nêu con số, ví dụ
     *         {@code "Đơn hàng cần tối thiểu 500.000 ₫ để dùng mã này."}
     */
    private String genMinOrderValueMessage(Long minOrderValue) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        long safeValue = minOrderValue == null ? 0L : minOrderValue;
        return "Đơn hàng cần tối thiểu " + formatter.format(safeValue) + " ₫ để dùng mã này.";
    }
}
