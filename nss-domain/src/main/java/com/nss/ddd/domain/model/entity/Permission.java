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
 * Quyền hạn đơn lẻ trong mô hình RBAC — thuần server-side, không bao giờ lộ ra response.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_code", columnNames = "code"))
@Comment("Quyen han don le trong mo hinh RBAC")
public class Permission {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code code} — mã quyền UPPER_SNAKE, ví dụ {@code PRODUCT_WRITE}. */
    @Column(nullable = false, length = 64)
    @Comment("Ma quyen dang UPPER_SNAKE, duy nhat")
    private String code;

    /** Tương ứng cột {@code name}. */
    @Column(nullable = false, length = 128)
    @Comment("Ten hien thi cua quyen")
    private String name;

    /** Tương ứng cột {@code description}. */
    @Column(length = 255)
    @Comment("Mo ta quyen")
    private String description;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem tao, luu theo gio UTC")
    private LocalDateTime createdAt;
}
