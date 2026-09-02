package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
 * Yêu cầu mua hàng bất đồng bộ — Luồng B (backlog 0039, architecture/01-overview.md §6).
 * <p>
 * <b>Bảng theo dõi VÒNG ĐỜI của một request, tách khỏi {@code customer_order}</b>: một request có
 * thể không bao giờ sinh ra đơn nào (giỏ trống, hết hàng…) nên nó không thể là một dòng của
 * {@code customer_order}. {@code orderCode} chỉ có giá trị khi {@code status = SUCCESS}.
 * <p>
 * <b>{@code idempotencyKey} ở đây KHÔNG phải bảng {@code idempotency_key} sẵn có.</b> Bảng cũ khoá
 * theo {@code event_id} (Long, server sinh) — chống Kafka redeliver ở consumer. Cột này khoá theo
 * chuỗi <i>client cung cấp</i> qua header {@code Idempotency-Key} — chống retry HTTP của client
 * (double-click, timeout+retry) xảy ra <i>trước khi</i> event Kafka nào tồn tại. Hai lớp idempotency
 * khác nhau, chống hai kiểu trùng lặp khác nhau, không gộp làm một.
 * <p>
 * <b>{@code requestId} là khoá tự nhiên</b> ({@code "PR-<16 hex>"}), không {@code @GeneratedValue} —
 * cùng khuôn với {@code IdempotencyKey.eventId} và mã đơn của {@code Order}: sinh ở tầng application
 * TRƯỚC khi ghi, để trả ngay cho client trong response {@code 202} mà không cần đọc lại.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "purchase_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key"),
        indexes = {
                // Truy van "PENDING cu nhat" cho observability (backlog 0039 Phase 7, gauge
                // purchase_request_pending_age_seconds) — dung DUNG hai cot nay theo dung thu tu.
                @Index(name = "idx_status_created", columnList = "status, created_at")
        }
)
@Comment("Yeu cau mua hang bat dong bo (Luong B), theo doi tu PENDING den SUCCESS/FAILED")
public class PurchaseRequest {

    /** Đang chờ consumer xử lý. */
    public static final int STATUS_PENDING = 0;

    /** Consumer đã tạo đơn thành công — {@link #orderCode} có giá trị. */
    public static final int STATUS_SUCCESS = 1;

    /** Consumer xử lý xong nhưng thất bại về nghiệp vụ (hết hàng, giỏ trống…) — {@link #failureCode} có giá trị. */
    public static final int STATUS_FAILED = 2;

    /** Tương ứng cột {@code request_id} — khoá tự nhiên, dạng {@code PR-<16 hex>}. */
    @Id
    @Column(length = 32)
    @Comment("Khoa chinh tu nhien, dang PR-<16 hex>")
    private String requestId;

    /**
     * Tương ứng cột {@code idempotency_key} — chuỗi client cung cấp qua header
     * {@code Idempotency-Key}, unique. Chống client retry HTTP, KHÁC bảng {@code idempotency_key}
     * (chống Kafka redeliver) — xem javadoc cấp class.
     */
    @Column(name = "idempotency_key", nullable = false, length = 128)
    @Comment("Khoa idempotency do CLIENT cung cap qua header Idempotency-Key, chong retry HTTP")
    private String idempotencyKey;

    /**
     * Tương ứng cột {@code user_id} — chủ request; {@code null} là khách vãng lai.
     * <p>
     * <b>Quan hệ {@code @ManyToOne}, không phải {@code Long} trần</b> — cùng khuôn
     * {@code Order.user}/{@code PasswordResetToken.user}: chỉ một quan hệ JPA thật mới khiến
     * Hibernate tự sinh ràng buộc khoá ngoại lúc {@code ddl-auto: update}. Một cột {@code Long}
     * trần biên dịch được, JPA vẫn ghi đúng giá trị, nhưng KHÔNG có ràng buộc nào trong DB — đo
     * được bằng {@code SchemaSmokeTest#foreignKeysExistInDatabase} (thiếu 1 khoá ngoại so với kỳ
     * vọng, không lỗi biên dịch nào báo trước).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_purchase_request_user"))
    @Comment("Chu request; null la khach vang lai")
    private User user;

    /** Tương ứng cột {@code status}. {@code // 0=PENDING, 1=SUCCESS, 2=FAILED} */
    @Column(nullable = false)
    @Comment("Trang thai xu ly: 0=PENDING, 1=SUCCESS, 2=FAILED")
    private Integer status;

    /** Tương ứng cột {@code order_code} — mã đơn đã tạo; chỉ có giá trị khi {@code status=SUCCESS}. */
    @Column(length = 32)
    @Comment("Ma don da tao, chi co gia tri khi status=SUCCESS")
    private String orderCode;

    /** Tương ứng cột {@code failure_code} — mã lỗi nghiệp vụ UPPER_SNAKE; chỉ có giá trị khi {@code status=FAILED}. */
    @Column(length = 64)
    @Comment("Ma loi nghiep vu UPPER_SNAKE, chi co gia tri khi status=FAILED")
    private String failureCode;

    /** Tương ứng cột {@code failure_message} — thông điệp tiếng Việt; chỉ có giá trị khi {@code status=FAILED}. */
    @Column(length = 500)
    @Comment("Thong diep tieng Viet cho nguoi dung cuoi, chi co gia tri khi status=FAILED")
    private String failureMessage;

    /** Tương ứng cột {@code created_at} — thời điểm submit, giờ UTC. */
    @Column(nullable = false)
    @Comment("Thoi diem submit, luu theo gio UTC")
    private LocalDateTime createdAt;

    /** Tương ứng cột {@code updated_at} — thời điểm chuyển trạng thái gần nhất, giờ UTC. */
    @Column(nullable = false)
    @Comment("Thoi diem cap nhat gan nhat, luu theo gio UTC")
    private LocalDateTime updatedAt;
}
