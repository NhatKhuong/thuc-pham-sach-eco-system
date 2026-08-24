package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.repository.CouponRepository;
import com.nss.ddd.domain.service.CouponDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Hiện thực domain service của {@code Coupon}.
 * <p>
 * Phụ thuộc duy nhất là port {@code CouponRepository} — không có tham chiếu nào tới module
 * infrastructure ở compile-time.
 * <p>
 * <b>Mọi phép so thời gian đều lấy {@code LocalDateTime.now(ZoneOffset.UTC)}, không bao giờ
 * {@code LocalDateTime.now()} trần.</b> Cột {@code starts_at} / {@code ends_at} lưu giờ UTC (javadoc
 * của {@code Coupon} nói vậy, và JDBC URL đặt {@code preserveInstants=false} để chuỗi đi qua nguyên
 * vẹn). Máy dev ở {@code Asia/Saigon} là UTC+7, nên một {@code now()} trần sẽ khiến mã hết hạn lúc
 * 17:00 hôm trước vẫn được coi là còn hạn suốt 7 tiếng — <b>và không có gì ném lỗi</b>. Đây đúng là
 * cái bẫy ticket 0008 đã cắn một lần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponDomainServiceImpl implements CouponDomainService {

    private final CouponRepository couponRepository;

    @Override
    public Coupon findByCode(String rawCode) {
        if (rawCode == null) {
            return null;
        }
        // §Contract 4: cat khoang trang hai dau; viec bo qua hoa thuong nam trong cau truy van
        String normalized = rawCode.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return couponRepository.findByCode(normalized).orElse(null);
    }

    @Override
    public boolean isRedeemable(Coupon coupon) {
        if (coupon == null) {
            return false;
        }
        // 1. Co bat/tat thu cong
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            return false;
        }
        // 2. Cua so thoi gian; dau nao null thi dau do khong gioi han
        LocalDateTime now = genNowUtc();
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            return false;
        }
        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            return false;
        }
        // 3. So luot; usageLimit null la khong gioi han
        if (coupon.getUsageLimit() == null) {
            return true;
        }
        int usedCount = coupon.getUsedCount() == null ? 0 : coupon.getUsedCount();
        return usedCount < coupon.getUsageLimit();
    }

    @Override
    public boolean meetsMinOrderValue(Coupon coupon, Long subtotal) {
        if (coupon == null) {
            return false;
        }
        long minOrderValue = coupon.getMinOrderValue() == null ? 0L : coupon.getMinOrderValue();
        long safeSubtotal = subtotal == null ? 0L : subtotal;
        return safeSubtotal >= minOrderValue;
    }

    @Override
    public List<Coupon> findRedeemableCoupons() {
        return couponRepository.findRedeemable(genNowUtc());
    }

    // ========== HELPERS ==========

    /**
     * Mốc thời gian dùng cho mọi phép so hiệu lực.
     * <p>
     * Gom vào một private method chứ không rải {@code LocalDateTime.now(ZoneOffset.UTC)} ở ba chỗ:
     * một chỗ quên {@code ZoneOffset.UTC} là một lệch 7 tiếng im lặng, và gom lại thì chỉ có đúng
     * một dòng phải đọc để biết quy ước.
     *
     * @return thời điểm hiện tại theo <b>giờ UTC</b>
     */
    private LocalDateTime genNowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
