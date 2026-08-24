package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.response.CouponResponse;
import com.nss.ddd.domain.model.entity.Coupon;

import java.util.Collections;
import java.util.List;

/**
 * Converter viết tay giữa {@code Coupon} và các kiểu của tầng application.
 * <p>
 * Class stateless, method {@code public static}, <b>không phải Spring bean</b> và luôn null-guard
 * (coding-conventions §7).
 * <p>
 * <b>Đây là "đúng một chỗ" của bảng dịch {@code type}</b> mà backlog 0014 §Contract 4 nói tới:
 * {@code 0 -> "percent"}, {@code 1 -> "fixed"}. DB lưu {@code int} (rẻ, có thứ tự, không phụ thuộc
 * chuỗi), dây mang chuỗi thường (frontend {@code switch} trên đúng hai giá trị đó). Nhân đôi bảng
 * dịch — một bản ở đây, một bản ở chỗ tính tiền của phase 3 — là cách chắc chắn nhất để hai bản
 * lệch nhau về sau, và triệu chứng sẽ là một mã {@code percent} bị tính như {@code fixed}: đơn
 * 500.000 ₫ giảm 10 ₫ thay vì 50.000 ₫, không có exception nào.
 * <p>
 * Danh sách trường trong {@link #toResponse} được viết ra bằng tay chính là chỗ chặn bảy cột nội bộ
 * ({@code isActive}, {@code startsAt}, {@code endsAt}, {@code usageLimit}, {@code usedCount},
 * {@code createdAt}, {@code updatedAt}) rò ra response.
 */
public final class CouponMapper {

    /** Giá trị {@code type} trong DB cho mã giảm theo phần trăm. */
    private static final int TYPE_PERCENT = 0;

    /** Giá trị {@code type} trong DB cho mã giảm số tiền cố định. */
    private static final int TYPE_FIXED = 1;

    /** Chuỗi {@code type} trên dây cho mã giảm theo phần trăm — khớp {@code src/types/cart.ts}. */
    public static final String WIRE_TYPE_PERCENT = "percent";

    /** Chuỗi {@code type} trên dây cho mã giảm số tiền cố định — khớp {@code src/types/cart.ts}. */
    public static final String WIRE_TYPE_FIXED = "fixed";

    /**
     * Class tiện ích, không có thể hiện.
     */
    private CouponMapper() {
    }

    /**
     * Dựng payload cho bề mặt dây — đúng năm trường của type {@code Coupon} phía client.
     *
     * @param coupon entity
     * @return payload, hoặc {@code null} khi {@code coupon} rỗng
     */
    public static CouponResponse toResponse(Coupon coupon) {
        if (coupon == null) {
            return null;
        }
        return new CouponResponse()
                .setCode(coupon.getCode())
                .setType(toWireType(coupon.getType()))
                .setValue(coupon.getValue())
                .setMinOrderValue(coupon.getMinOrderValue())
                .setDescription(coupon.getDescription());
    }

    /**
     * @param coupons entity, thứ tự giữ nguyên như repository trả về
     * @return danh sách payload; danh sách rỗng khi đầu vào rỗng
     */
    public static List<CouponResponse> toResponses(List<Coupon> coupons) {
        if (coupons == null || coupons.isEmpty()) {
            return Collections.emptyList();
        }
        return coupons.stream()
                .map(CouponMapper::toResponse)
                .toList();
    }

    /**
     * Bảng dịch {@code int} của DB sang chuỗi thường của dây.
     * <p>
     * <b>Giá trị lạ trả {@code null} chứ không đoán.</b> Cột {@code type} hôm nay chỉ có 0 và 1;
     * một giá trị 2 xuất hiện nghĩa là ai đó đã thêm loại mã mới mà quên cập nhật bảng này. Rơi về
     * một trong hai chuỗi cũ sẽ khiến frontend tính tiền theo loại sai và <i>vẫn ra một con số</i>;
     * {@code null} thì hỏng ngay và hỏng ở chỗ đọc được.
     *
     * @param type giá trị cột {@code type}
     * @return {@code "percent"} / {@code "fixed"}, hoặc {@code null} khi giá trị không nằm trong
     *         bảng dịch
     */
    private static String toWireType(Integer type) {
        if (type == null) {
            return null;
        }
        if (type == TYPE_PERCENT) {
            return WIRE_TYPE_PERCENT;
        }
        if (type == TYPE_FIXED) {
            return WIRE_TYPE_FIXED;
        }
        return null;
    }
}
