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

/**
 * Một ảnh của sản phẩm — hiện thực của {@code Product.images: string[]} phía client.
 * <p>
 * Bảng con thật thay vì {@code @ElementCollection}: cần thứ tự hiển thị ({@code sort_order})
 * và cần khóa ngoại thật về {@code product}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(indexes = @Index(name = "idx_product_id", columnList = "product_id"))
@Comment("Anh cua san pham, hien thuc cua Product.images")
public class ProductImage {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code product_id}. Quan hệ một chiều LAZY, sinh khóa ngoại thật. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_product_image_product"))
    @Comment("San pham so huu anh nay")
    private Product product;

    /** Tương ứng cột {@code url} — đường dẫn tương đối dạng {@code /images/...} (§A.5). */
    @Column(nullable = false, length = 255)
    @Comment("Duong dan anh tuong doi, bat dau bang /images/")
    private String url;

    /** Tương ứng cột {@code sort_order} — thứ tự hiển thị trong gallery, nhỏ hơn đứng trước. */
    @Column(nullable = false)
    @Comment("Thu tu hien thi trong gallery, nho hon dung truoc")
    private Integer sortOrder;
}
