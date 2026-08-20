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
 * Bảng nối role ↔ permission. Cùng lý do thiết kế như {@link UserRole}:
 * entity riêng có {@code Long id}, tính duy nhất của cặp do unique constraint giữ.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_role_id_permission_id", columnNames = {"role_id", "permission_id"}),
        indexes = @Index(name = "idx_permission_id", columnList = "permission_id")
)
@Comment("Bang noi role - permission")
public class RolePermission {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code role_id}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_role_permission_role"))
    @Comment("Vai tro duoc cap quyen")
    private Role role;

    /** Tương ứng cột {@code permission_id}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_role_permission_permission"))
    @Comment("Quyen duoc cap")
    private Permission permission;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem cap quyen, luu theo gio UTC")
    private LocalDateTime createdAt;
}
