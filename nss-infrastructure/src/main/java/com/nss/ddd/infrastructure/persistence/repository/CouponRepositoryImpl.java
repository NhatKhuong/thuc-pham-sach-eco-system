package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.repository.CouponRepository;
import com.nss.ddd.infrastructure.persistence.mapper.CouponJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ADAPTER cho port {@code CouponRepository}.
 * <p>
 * Mọi khái niệm của Spring Data dừng lại ở file này; phía trên chỉ thấy kiểu của domain.
 * <p>
 * Stereotype là {@code @Repository}, không phải {@code @Service} — coding-conventions §3.
 */
@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJPAMapper couponJPAMapper;

    @Override
    public Optional<Coupon> findByCode(String code) {
        return couponJPAMapper.findByCodeIgnoreCase(code);
    }

    @Override
    public List<Coupon> findRedeemable(LocalDateTime now) {
        return couponJPAMapper.findRedeemable(now);
    }

    @Override
    public boolean increaseUsedCount(String code) {
        // Rows-affected la khai niem cua tang nay; domain chi thay boolean (coding-conventions §12)
        return couponJPAMapper.increaseUsedCount(code) > 0;
    }
}
