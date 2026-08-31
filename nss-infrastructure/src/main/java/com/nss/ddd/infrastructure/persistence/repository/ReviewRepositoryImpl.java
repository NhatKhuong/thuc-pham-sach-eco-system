package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.RatingCount;
import com.nss.ddd.domain.model.entity.Review;
import com.nss.ddd.domain.repository.ReviewRepository;
import com.nss.ddd.infrastructure.persistence.mapper.ReviewJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ADAPTER cho port {@code ReviewRepository}.
 * <p>
 * Đây là <b>ranh giới</b>: khái niệm rows-affected của Spring Data dừng lại ở file này và đi lên
 * trên dưới dạng {@code boolean} — cùng khuôn với {@code ProductRepositoryImpl#decreaseStock}.
 * Phía trên không cần biết luật chống trùng được thi hành bằng {@code INSERT IGNORE} hay bằng gì
 * khác, chỉ cần biết "ghi được" hay "đã có rồi".
 */
@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {

    private final ReviewJPAMapper reviewJPAMapper;

    @Override
    public List<Review> findByProductId(Long productId) {
        return reviewJPAMapper.findByProductId(productId);
    }

    @Override
    public List<RatingCount> countGroupedByRating(Long productId) {
        return reviewJPAMapper.countGroupedByRating(productId);
    }

    @Override
    public long countByProductId(Long productId) {
        return reviewJPAMapper.countByProductId(productId);
    }

    @Override
    public long sumRatingByProductId(Long productId) {
        return reviewJPAMapper.sumRatingByProductId(productId);
    }

    @Override
    public boolean insertIfAbsent(Long productId, Long userId, String authorName, Integer rating,
                                  String content, LocalDateTime createdAt) {
        return reviewJPAMapper.insertIgnore(productId, userId, authorName, rating, content, createdAt) > 0;
    }

    @Override
    public Optional<Review> findByProductIdAndUserId(Long productId, Long userId) {
        return reviewJPAMapper.findByProductIdAndUserId(productId, userId);
    }
}
