package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Tài khoản người dùng.
 * <p>
 * <b>{@code password_hash} không bao giờ được lộ ra response</b> — §B.4 #1 nói rõ
 * {@code User} trả về không chứa password kể cả dạng đã hash. Tầng DTO phải bỏ trường này.
 * <p>
 * <b>RBAC là thuần server-side.</b> {@code types/user.ts#User} chỉ có
 * {@code id, fullName, email, phone, avatar}; {@link Role} và {@link Permission}
 * không được rò ra {@code User} response ở bất kỳ ticket nào sau này.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_email", columnNames = "email"))
@Comment("Tai khoan nguoi dung")
public class User {

    /** Tương ứng cột {@code id}. §B.4 #2: {@code updateProfile} không được cho client ghi đè trường này. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code full_name}. */
    @Column(nullable = false, length = 128)
    @Comment("Ho ten day du")
    private String fullName;

    /** Tương ứng cột {@code email} — duy nhất; §B.4 trả 409 khi trùng. */
    @Column(nullable = false, length = 160)
    @Comment("Email dang nhap, duy nhat toan he")
    private String email;

    /** Tương ứng cột {@code phone}. */
    @Column(nullable = false, length = 20)
    @Comment("So dien thoai lien he")
    private String phone;

    /** Tương ứng cột {@code avatar} — nullable, đường dẫn tương đối {@code /images/...} (§A.5). */
    @Column(length = 255)
    @Comment("Duong dan anh dai dien tuong doi; null neu chua co")
    private String avatar;

    /**
     * Tương ứng cột {@code password_hash} — <b>chỉ tồn tại phía server</b>.
     * Không có trường tương ứng trong {@code types/user.ts#User}, và đó là chủ ý (§B.4 #1).
     */
    @Column(nullable = false, length = 100)
    @Comment("Bam mat khau; tuyet doi khong tra ra response")
    private String passwordHash;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem tao, luu theo gio UTC")
    private LocalDateTime createdAt;

    /** Tương ứng cột {@code updated_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Comment("Thoi diem cap nhat gan nhat, luu theo gio UTC")
    private LocalDateTime updatedAt;
}
