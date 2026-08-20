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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sản phẩm — gốc của luồng "quản lý sản phẩm" và là dữ liệu tra cứu nóng nhất của hệ.
 * <p>
 * Hai cột phái sinh tồn tại vì API_CONTRACT §B.1, không phải vì tối ưu sớm:
 * <ul>
 *   <li>{@code name_normalized} — §B.1 bắt tìm kiếm phải <b>bỏ dấu</b> ("cam" khớp
 *       "Cam sành hữu cơ"). Collation {@code utf8mb4_unicode_ci} gập được hoa/thường và
 *       dấu thanh nhưng <b>không gập {@code đ} thành {@code d}</b>, nên phải có cột chuẩn
 *       hoá sẵn. Ticket này chỉ tạo cột; việc điền là của tầng service.</li>
 *   <li>{@code effective_price} — §B.1 cảnh báo cả <b>lọc</b> lẫn <b>sắp xếp</b> theo giá
 *       đều phải dùng {@code salePrice ?? price}. Viết {@code COALESCE(...)} trong
 *       {@code WHERE} thì MySQL bỏ index; cột sinh STORED có index thì không.</li>
 * </ul>
 * {@code images: string[]} của client nằm ở bảng con {@link ProductImage}, không dùng
 * {@code @ElementCollection}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_slug", columnNames = "slug"),
        indexes = {
                @Index(name = "idx_name_normalized", columnList = "name_normalized"),
                @Index(name = "idx_effective_price", columnList = "effective_price"),
                @Index(name = "idx_category_id", columnList = "category_id"),
                @Index(name = "idx_brand_id", columnList = "brand_id")
        }
)
@Comment("San pham")
public class Product {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code slug} — khóa tra cứu của {@code GET /products/{slug}}. */
    @Column(nullable = false, length = 160)
    @Comment("Slug khong dau, duy nhat, dung lam duong dan")
    private String slug;

    /** Tương ứng cột {@code name}. */
    @Column(nullable = false, length = 255)
    @Comment("Ten hien thi cua san pham")
    private String name;

    /**
     * Tương ứng cột {@code name_normalized} — bản {@code name} đã bỏ dấu và hạ về chữ thường,
     * dùng cho {@code q} ở §B.1. Tầng service điền, ticket này chỉ tạo cột.
     */
    @Column(length = 255)
    @Comment("Ten da bo dau va ha chu thuong, phuc vu tim kiem khong dau")
    private String nameNormalized;

    /** Tương ứng cột {@code short_description} — {@code q} tìm cả trong trường này (§B.1). */
    @Column(length = 500)
    @Comment("Mo ta ngan hien thi tren the san pham")
    private String shortDescription;

    /** Tương ứng cột {@code description}. */
    @Column(columnDefinition = "TEXT")
    @Comment("Mo ta day du cua san pham")
    private String description;

    /** Tương ứng cột {@code price} — số nguyên VNĐ (§A.5); {@code Long} vì tổng đơn cộng dồn chạm trần {@code int}. */
    @Column(nullable = false)
    @Comment("Gia goc, so nguyen VND")
    private Long price;

    /** Tương ứng cột {@code sale_price} — {@code null} nghĩa là không giảm giá, không dùng 0 (§A.5). */
    @Comment("Gia khuyen mai, so nguyen VND; null la khong giam gia")
    private Long salePrice;

    /**
     * Tương ứng cột {@code effective_price} — <b>cột sinh</b>
     * {@code GENERATED ALWAYS AS (COALESCE(sale_price, price)) STORED}, có index.
     * <p>
     * Chỉ đọc: {@code insertable=false, updatable=false} để Hibernate không bao giờ ghi vào nó.
     * Đây là cột mà mọi bộ lọc {@code minPrice}/{@code maxPrice} và mọi sắp xếp
     * {@code price_asc}/{@code price_desc} phải dùng.
     */
    @Column(insertable = false, updatable = false,
            columnDefinition = "BIGINT GENERATED ALWAYS AS (COALESCE(sale_price, price)) STORED")
    @Comment("Gia thuc te phai tra = COALESCE(sale_price, price), cot sinh STORED")
    private Long effectivePrice;

    /** Tương ứng cột {@code unit} — đơn vị hiển thị cạnh giá: "kg", "bó", "hộp". */
    @Column(nullable = false, length = 32)
    @Comment("Don vi tinh hien thi canh gia")
    private String unit;

    /** Tương ứng cột {@code origin}. */
    @Column(length = 128)
    @Comment("Xuat xu san pham")
    private String origin;

    /** Tương ứng cột {@code stock} — 0 nghĩa là hết hàng. Là con số backend kiểm lại khi đặt đơn (§C.1). */
    @Column(nullable = false)
    @Comment("So luong con trong kho; 0 la het hang")
    private Integer stock;

    /** Tương ứng cột {@code sold} — cơ sở cho sắp xếp {@code best_selling} (§B.1). */
    @Column(nullable = false)
    @Comment("So luong da ban, dung cho sap xep best_selling")
    private Integer sold;

    /**
     * Tương ứng cột {@code rating} — {@code DECIMAL(2,1)}, thang 0.0–5.0.
     * Backend tính lại mỗi khi có đánh giá mới (§C.3), tầng service lo ở ticket sau.
     */
    @Column(nullable = false, precision = 2, scale = 1)
    @Comment("Diem danh gia trung binh, thang 0.0-5.0")
    private BigDecimal rating;

    /** Tương ứng cột {@code review_count} — lưu sẵn, backend tính lại khi có đánh giá mới (§C.3). */
    @Column(nullable = false)
    @Comment("So luot danh gia, tinh lai khi co danh gia moi")
    private Integer reviewCount;

    /** Tương ứng cột {@code is_featured} — bộ lọc {@code isFeatured} ở §B.1. */
    @Column(nullable = false)
    @Comment("Co phai san pham noi bat")
    private Boolean isFeatured;

    /** Tương ứng cột {@code is_best_seller} — bộ lọc {@code isBestSeller} ở §B.1. */
    @Column(nullable = false)
    @Comment("Co phai san pham ban chay")
    private Boolean isBestSeller;

    /** Tương ứng cột {@code category_id}. Quan hệ một chiều LAZY, sinh khóa ngoại thật. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_product_category"))
    @Comment("Danh muc cua san pham")
    private Category category;

    /** Tương ứng cột {@code brand_id} — nullable, khớp {@code brandId: number | null} của client. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_product_brand"))
    @Comment("Thuong hieu cua san pham; null neu khong gan")
    private Brand brand;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. Cơ sở cho sắp xếp {@code newest}. */
    @Column(nullable = false)
    @Comment("Thoi diem tao, luu theo gio UTC; co so cho sap xep newest")
    private LocalDateTime createdAt;

    /** Tương ứng cột {@code updated_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Comment("Thoi diem cap nhat gan nhat, luu theo gio UTC")
    private LocalDateTime updatedAt;
}
