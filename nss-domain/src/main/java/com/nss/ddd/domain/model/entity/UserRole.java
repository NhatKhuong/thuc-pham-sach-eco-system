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
 * Bảng nối user ↔ role.
 * <p>
 * Là <b>entity riêng có {@code Long id}</b> thay vì {@code @EmbeddedId} hay {@code @ManyToMany}:
 * {@code coding-conventions.md} §6 chỉ định nghĩa hai kiểu khóa — IDENTITY và natural key trần —
 * nên {@code @EmbeddedId} sẽ là một quy ước mới mà tài liệu chưa có. Tính duy nhất của cặp
 * do {@code uk_user_id_role_id} giữ, không do khóa chính.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_user_id_role_id", columnNames = {"user_id", "role_id"}),
        indexes = @Index(name = "idx_role_id", columnList = "role_id")
)
@Comment("Bang noi user - role")
public class UserRole {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code user_id}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_user_role_user"))
    @Comment("Nguoi dung duoc gan vai tro")
    private User user;

    /** Tương ứng cột {@code role_id}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_user_role_role"))
    @Comment("Vai tro duoc gan")
    private Role role;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem gan vai tro, luu theo gio UTC")
    private LocalDateTime createdAt;
}
