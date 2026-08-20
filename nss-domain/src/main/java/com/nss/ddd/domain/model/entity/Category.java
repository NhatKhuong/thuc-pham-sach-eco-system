package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Danh mục sản phẩm — cây tự tham chiếu, không giới hạn số cấp.
 * <p>
 * Thêm ngành hàng mới ("hải sản", "đồ khô") là một lệnh INSERT, không phải đổi schema.
 * <p>
 * {@code Category.productCount} của client <b>cố ý không phải cột</b>: API_CONTRACT §B.2 nói
 * backend tính con số này, và với danh mục gốc nó phải gồm cả sản phẩm của danh mục con —
 * một cột lưu sẵn sẽ sai ngay khi thêm hoặc bớt một sản phẩm ở nhánh con.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_slug", columnNames = "slug"))
@Comment("Danh muc san pham, cay tu tham chieu qua parent_id")
public class Category {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code slug} — khóa tra cứu của {@code GET /categories/{slug}}. */
    @Column(nullable = false, length = 160)
    @Comment("Slug khong dau, duy nhat, dung lam duong dan")
    private String slug;

    /** Tương ứng cột {@code name}. */
    @Column(nullable = false, length = 160)
    @Comment("Ten hien thi cua danh muc")
    private String name;

    /** Tương ứng cột {@code description}. */
    @Column(length = 500)
    @Comment("Mo ta ngan cua danh muc")
    private String description;

    /** Tương ứng cột {@code image} — đường dẫn tương đối dạng {@code /images/...} (§A.5). */
    @Column(length = 255)
    @Comment("Duong dan anh tuong doi, bat dau bang /images/")
    private String image;

    /**
     * Tương ứng cột {@code parent_id}. {@code null} = danh mục gốc.
     * <p>
     * Quan hệ một chiều LAZY: không có {@code @OneToMany} chiều ngược lại, nên
     * {@code toString()} của Lombok {@code @Data} không thể đệ quy vô hạn.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_category_parent"))
    @Comment("Danh muc cha; null la danh muc goc")
    private Category parent;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem tao, luu theo gio UTC")
    private LocalDateTime createdAt;

    /** Tương ứng cột {@code updated_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Comment("Thoi diem cap nhat gan nhat, luu theo gio UTC")
    private LocalDateTime updatedAt;
}
