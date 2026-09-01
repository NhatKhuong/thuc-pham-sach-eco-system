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
 * Token xác nhận email đã phát cho một tài khoản vừa {@code POST /api/auth/register}, hoặc phát lại
 * qua {@code POST /api/auth/resend-confirmation} (backlog 0037).
 * <p>
 * <b>Đi đúng khuôn {@code PasswordResetToken}</b> — cùng cấu trúc dòng, cùng cách hash, cùng cách
 * tiêu một lần bằng UPDATE có điều kiện. Bảng riêng chứ không nhét vào {@code password_reset_token}
 * vì hai loại token có vòng đời khác nhau (một cái xác nhận danh tính hộp thư, một cái đổi được mật
 * khẩu) và không có gì buộc chúng phải sống chung một bảng.
 * <p>
 * <b>{@code token_hash} chứ không phải {@code token} thô</b> — cùng lý do đã viết ở
 * {@code PasswordResetToken}: một lần rò đọc DB trên bảng này cho kẻ tấn công quyền tự xác nhận email
 * của bất kỳ tài khoản nào đang chờ xác nhận. Chuỗi thô chỉ tồn tại trong bộ nhớ đúng một lần — lúc
 * sinh ra — rồi đi thẳng vào link trong email. <b>Không bao giờ ghi nó xuống DB, và không bao giờ đưa
 * vào log.</b>
 * <p>
 * Hash là SHA-256, không phải bcrypt — cùng lý do đã viết ở {@code PasswordResetToken}: token là 256
 * bit ngẫu nhiên mã hoá, không có từ điển nào để dò, nên một hàm băm nhanh và tra được bằng
 * {@code WHERE token_hash = ?} là đủ.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_user_id", columnList = "user_id")
)
@Comment("Token xac nhan email, dung mot lan, luu duoi dang hash")
public class EmailConfirmationToken {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code user_id} — chủ tài khoản đang chờ xác nhận. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_email_confirmation_token_user"))
    @Comment("Nguoi dung dang cho xac nhan email")
    private User user;

    /**
     * Tương ứng cột {@code token_hash} — <b>SHA-256 của chuỗi thô, viết hex thường, đúng 64 ký tự</b>.
     * Chuỗi thô không có mặt ở bất kỳ cột nào của bảng này; xem javadoc cấp class.
     */
    @Column(nullable = false, length = 64)
    @Comment("SHA-256 cua token dang hex, duy nhat; chuoi tho khong bao gio duoc luu")
    private String tokenHash;

    /** Tương ứng cột {@code expires_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)} cộng thời hạn. */
    @Column(nullable = false)
    @Comment("Thoi diem het han, luu theo gio UTC")
    private LocalDateTime expiresAt;

    /**
     * Tương ứng cột {@code is_used} — {@code true} sau khi token đã xác nhận được một email.
     * <p>
     * UPDATE có điều kiện {@code SET is_used = true WHERE ... AND is_used = false} là nơi "dùng đúng
     * một lần" thật sự được cưỡng chế — cùng cơ chế {@code PasswordResetToken.isUsed}.
     */
    @Column(nullable = false)
    @Comment("Da dung hay chua; xac nhan thanh cong dat cot nay thanh true")
    private Boolean isUsed;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem phat token, luu theo gio UTC")
    private LocalDateTime createdAt;
}
