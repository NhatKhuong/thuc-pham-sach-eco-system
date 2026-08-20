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
 * Refresh token đã phát cho một phiên đăng nhập.
 * <p>
 * Bảng này tồn tại vì §B.4 #3: {@code POST /auth/logout} phải <b>thu hồi</b> refresh token.
 * Không có bảng thì không có chỗ ghi "token này đã bị thu hồi", và token vẫn dùng được cho tới
 * lúc hết hạn dù người dùng đã thoát — nghĩa là nút Đăng xuất chỉ có tác dụng trên giao diện.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_token", columnNames = "token"),
        indexes = @Index(name = "idx_user_id", columnList = "user_id")
)
@Comment("Refresh token da phat cho mot phien dang nhap")
public class RefreshToken {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code user_id} — chủ của token. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_token_user"))
    @Comment("Nguoi dung so huu token")
    private User user;

    /** Tương ứng cột {@code token} — duy nhất; 512 ký tự utf8mb4 = 2048 byte, vẫn dưới trần index 3072 byte của InnoDB. */
    @Column(nullable = false, length = 512)
    @Comment("Chuoi refresh token, duy nhat")
    private String token;

    /** Tương ứng cột {@code expires_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)} cộng thời hạn. */
    @Column(nullable = false)
    @Comment("Thoi diem het han, luu theo gio UTC")
    private LocalDateTime expiresAt;

    /** Tương ứng cột {@code is_revoked} — {@code true} sau khi {@code logout} thu hồi (§B.4 #3). */
    @Column(nullable = false)
    @Comment("Da bi thu hoi hay chua; logout dat cot nay thanh true")
    private Boolean isRevoked;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem phat token, luu theo gio UTC")
    private LocalDateTime createdAt;
}
