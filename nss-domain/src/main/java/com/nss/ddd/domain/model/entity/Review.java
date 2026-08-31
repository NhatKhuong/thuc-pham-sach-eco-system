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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Đánh giá của khách về một sản phẩm.
 * <p>
 * <b>{@code POST /api/products/{id}/reviews} YÊU CẦU token và bản ghi lưu {@code user_id} thật</b>
 * (ADR 0008, backlog 0027). Bảng này được backlog 0004 dựng theo giả định <i>ngược lại</i> —
 * javadoc cũ ghi <i>"§B.8 cho phép đánh giá không cần đăng nhập, nên bảng lưu author_name dạng
 * chuỗi chứ không khóa ngoại về user"</i> — và giả định đó đã bị đảo. {@code API_CONTRACT.md} §B.8
 * vẫn khai ô {@code Auth} là ⬜ vì nguồn của nó nằm ở board frontend; <b>ADR 0008 thắng</b>.
 * <p>
 * <b>{@link #authorName} KHÔNG bị thay bằng {@link #user} — hai trường tồn tại song song và mang
 * hai vai khác nhau.</b> {@code authorName} là <i>tên hiển thị</i> người dùng tự khai (nên
 * {@code CreateReviewPayload} của frontend không đổi một trường nào); {@code user} là <i>danh
 * tính</i>. Hai thứ có thể chọi nhau — ai đó ký tên khác tên tài khoản — và ADR 0008 cố ý
 * <b>không kiểm</b> điều đó.
 * <p>
 * <b>{@link #user} là cột NỘI BỘ, tuyệt đối không ra response.</b> {@code types/product.ts#Review}
 * của frontend có đúng sáu trường và {@code userId} không nằm trong đó. Chỗ chặn là
 * {@code ReviewMapper}: nó liệt kê tay từng trường thay vì map tự động, đúng kỷ luật đã chặn
 * {@code isActive} rò ra {@code ProductResponse} ở backlog 0008.
 * <p>
 * <b>{@link #user} NULLABLE, nhưng service luôn ghi giá trị.</b> 48 đánh giá đã seed từ
 * {@code reviews.json} của frontend không có tài khoản nào — chúng chỉ có {@code authorName}.
 * {@code NOT NULL} sẽ làm seed gãy, hoặc buộc phải bịa tài khoản cho 48 bản ghi. Cột nullable thì
 * dữ liệu cũ giữ {@code NULL} và <b>{@code uk_review_product_user} bỏ qua chúng</b> — MySQL không
 * coi hai {@code NULL} là trùng nhau trong unique index — nên không phải backfill gì.
 * <p>
 * <b>{@code uk_review_product_user} là nơi luật "mỗi tài khoản một đánh giá mỗi sản phẩm" thật sự
 * sống.</b> Đường ghi <i>không</i> được {@code SELECT} rồi {@code INSERT} để tự phát hiện trùng:
 * đó là đọc-rồi-ghi, đúng cơ chế đã sinh ra {@code bugs/0004} (hai đơn đồng thời đọc cùng một
 * {@code count} ⇒ trùng mã ⇒ 500). Ràng buộc này cắn trước, và tầng trên dịch kết quả thành 409 —
 * xem {@code ReviewJPAMapper#insertIgnore}.
 * <p>
 * {@code ReviewSummary} của client ({@code average}, {@code total}, {@code distribution})
 * <b>cố ý không có bảng</b>: nó là kết quả {@code GROUP BY rating} trên chính bảng này.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_review_product_user",
                columnNames = {"product_id", "user_id"}),
        indexes = @Index(name = "idx_product_id", columnList = "product_id")
)
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

    /**
     * Tương ứng cột {@code user_id} — <b>nullable</b>, {@code null} là bản ghi seed có trước
     * ADR 0008. Xem javadoc cấp class: cột nội bộ, không ra response; đường ghi luôn điền giá trị.
     * <p>
     * Giá trị này backend lấy từ claim {@code sub} của access token, <b>client không bao giờ gửi
     * lên</b> — cùng kỷ luật với {@code Order#user} (§C.4.1).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_review_user"))
    @Comment("Tai khoan viet danh gia; null la ban ghi seed co truoc ADR 0008")
    private User user;

    /** Tương ứng cột {@code author_name} — <b>tên hiển thị</b> người dùng tự khai, không phải danh tính. */
    @Column(nullable = false, length = 128)
    @Comment("Ten nguoi danh gia tu khai")
    private String authorName;

    /** Tương ứng cột {@code rating} — số nguyên 1–5, tầng DTO chặn giá trị ngoài khoảng (§B.8). */
    @Column(nullable = false)
    @Comment("Diem danh gia, so nguyen tu 1 den 5")
    private Integer rating;

    /** Tương ứng cột {@code content} — §B.8 yêu cầu tối thiểu 10 ký tự, tầng DTO kiểm. */
    @Column(nullable = false, columnDefinition = "TEXT")
    @Comment("Noi dung danh gia, toi thieu 10 ky tu")
    private String content;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem tao, luu theo gio UTC")
    private LocalDateTime createdAt;
}
