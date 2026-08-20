package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Đánh giá của khách về một sản phẩm.
 * <p>
 * §B.8 cho phép đánh giá <b>không cần đăng nhập</b> ({@code createReview} là endpoint công khai),
 * nên bảng lưu {@code author_name} dạng chuỗi chứ không khóa ngoại về {@code user}.
 * <p>
 * {@code ReviewSummary} của client ({@code average}, {@code total}, {@code distribution})
 * <b>cố ý không có bảng</b>: nó là kết quả {@code GROUP BY rating} trên chính bảng này.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(indexes = @Index(name = "idx_product_id", columnList = "product_id"))
@Comment("Danh gia cua khach ve san pham")
public class Review {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code product_id}. Quan hệ một chiều LAZY, sinh khóa ngoại thật. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_review_product"))
    @Comment("San pham duoc danh gia")
    private Product product;

    /** Tương ứng cột {@code author_name} — người đánh giá tự khai, không cần tài khoản. */
    @Column(nullable = false, length = 128)
    @Comment("Ten nguoi danh gia tu khai")
    private String authorName;

    /** Tương ứng cột {@code rating} — số nguyên 1–5, tầng service chặn giá trị ngoài khoảng (§B.8). */
    @Column(nullable = false)
    @Comment("Diem danh gia, so nguyen tu 1 den 5")
    private Integer rating;

    /** Tương ứng cột {@code content} — §B.8 yêu cầu tối thiểu 10 ký tự, tầng service kiểm. */
    @Column(nullable = false, columnDefinition = "TEXT")
    @Comment("Noi dung danh gia, toi thieu 10 ky tu")
    private String content;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem tao, luu theo gio UTC")
    private LocalDateTime createdAt;
}
