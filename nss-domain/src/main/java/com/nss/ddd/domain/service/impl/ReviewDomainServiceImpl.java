package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.RatingCount;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.Review;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.repository.ReviewRepository;
import com.nss.ddd.domain.service.ReviewDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Hiện thực domain service của {@code Review}.
 * <p>
 * Phụ thuộc duy nhất là hai port — không có tham chiếu nào tới module infrastructure ở compile-time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewDomainServiceImpl implements ReviewDomainService {

    /** Điểm đánh giá của sản phẩm chưa có lượt nào — {@code DECIMAL(2,1)} nên phải đúng 1 chữ số thập phân. */
    private static final BigDecimal RATING_NONE = new BigDecimal("0.0");

    /** Số chữ số thập phân của {@code product.rating} — cột là {@code DECIMAL(2,1)}. */
    private static final int RATING_SCALE = 1;

    private final ReviewRepository reviewRepository;

    private final ProductRepository productRepository;

    // ========== READ ==========

    @Override
    public List<Review> findByProductId(Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    @Override
    public List<RatingCount> countGroupedByRating(Long productId) {
        return reviewRepository.countGroupedByRating(productId);
    }

    // ========== WRITE ==========

    @Override
    public Review create(Long productId, Long userId, String authorName, Integer rating, String content) {
        // 1. Thoi diem: LocalDateTime.now(ZoneOffset.UTC), KHONG phai now() — now() lay gio may,
        //    lech 7 tieng o VN va khong co gi bao loi. Cat toi micro cho khop datetime(6).
        LocalDateTime now = genUtcNow();
        // 2. De rang buoc uk_review_product_user quyet dinh, khong SELECT roi INSERT (ADR 0008,
        //    bugs/0004). 0 dong anh huong = tai khoan nay da danh gia san pham nay roi.
        if (!reviewRepository.insertIfAbsent(productId, userId, authorName, rating, content, now)) {
            log.warn("create: duplicate review rejected by unique constraint | productId={} userId={}",
                    productId, userId);
            return null;
        }
        // 3. Doc lai de lay id — mot phep doc SAU mot lan ghi da chac chan thanh cong, khong phai
        //    mot phep kiem trung.
        Review saved = reviewRepository.findByProductIdAndUserId(productId, userId).orElse(null);
        log.info("create: saved review | reviewId={} productId={} userId={} rating={}",
                saved == null ? null : saved.getId(), productId, userId, rating);
        return saved;
    }

    @Override
    public Product recalcRatingStats(Product product) {
        long reviewCount = reviewRepository.countByProductId(product.getId());
        long sumRating = reviewRepository.sumRatingByProductId(product.getId());
        BigDecimal rating = genRating(sumRating, reviewCount);
        product.setRating(rating)
                .setReviewCount((int) reviewCount)
                .setUpdatedAt(genUtcNow());
        Product saved = productRepository.save(product);
        log.info("recalcRatingStats: success | productId={} rating={} reviewCount={}",
                saved.getId(), rating, reviewCount);
        return saved;
    }

    @Override
    public BigDecimal genRating(long sumRating, long reviewCount) {
        if (reviewCount <= 0) {
            return RATING_NONE;
        }
        // RoundingMode.HALF_UP tuong minh (coding-conventions §15): mac dinh cua setScale la
        // HALF_EVEN va no cho san pham 11 ra 4.2 thay vi 4.3.
        return BigDecimal.valueOf(sumRating)
                .divide(BigDecimal.valueOf(reviewCount), RATING_SCALE, RoundingMode.HALF_UP);
    }

    // ========== HELPERS ==========

    /**
     * Mốc thời gian hiện tại theo <b>giờ UTC</b>, cắt tới micro giây cho khớp {@code datetime(6)}.
     *
     * @return thời điểm hiện tại, giờ UTC
     */
    private LocalDateTime genUtcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
