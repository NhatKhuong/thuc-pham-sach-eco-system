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
 * Một dòng hàng trong đơn — <b>bản chụp</b> của sản phẩm tại thời điểm đặt.
 * <p>
 * Cố ý <b>không join sang {@link Product}</b>: {@code product_id} là cột {@code BIGINT} trần,
 * không có khóa ngoại. Lý do là mục đích của bảng này ngược với mục đích của khóa ngoại — đơn cũ
 * phải giữ nguyên tên, ảnh, đơn vị và giá đã bán, kể cả khi sản phẩm đổi giá, đổi tên hoặc bị gỡ
 * khỏi cửa hàng. Có khóa ngoại thì việc gỡ một sản phẩm sẽ bị chặn bởi chính lịch sử bán hàng.
 * <p>
 * {@code CartItem.stock} của client <b>cố ý không có cột</b> ở đây: nó là tồn kho tại thời điểm
 * thêm vào giỏ, chỉ dùng để chặn tăng số lượng trên giao diện. Khi đặt đơn, §C.1 bắt backend
 * kiểm lại tồn kho thật từ {@link Product}, nên lưu lại con số của client vừa vô dụng vừa dễ bị tin nhầm.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_product_id", columnList = "product_id")
})
@Comment("Dong hang trong don, la ban chup san pham tai thoi diem dat")
public class OrderItem {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code order_id}. Quan hệ một chiều LAZY, sinh khóa ngoại thật. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
    @Comment("Don hang chua dong nay")
    private Order order;

    /** Tương ứng cột {@code product_id} — <b>không có khóa ngoại</b>, xem javadoc cấp class. */
    @Column(nullable = false)
    @Comment("ID san pham tai thoi diem dat; co y khong co khoa ngoai")
    private Long productId;

    /** Tương ứng cột {@code slug} — bản chụp, để dựng link về trang sản phẩm. */
    @Column(nullable = false, length = 160)
    @Comment("Slug san pham, ban chup tai thoi diem dat")
    private String slug;

    /** Tương ứng cột {@code name} — bản chụp tên sản phẩm. */
    @Column(nullable = false, length = 255)
    @Comment("Ten san pham, ban chup tai thoi diem dat")
    private String name;

    /** Tương ứng cột {@code image} — bản chụp, đường dẫn tương đối {@code /images/...} (§A.5). */
    @Column(length = 255)
    @Comment("Anh san pham, ban chup, duong dan tuong doi")
    private String image;

    /** Tương ứng cột {@code unit} — bản chụp đơn vị tính. */
    @Column(nullable = false, length = 32)
    @Comment("Don vi tinh, ban chup tai thoi diem dat")
    private String unit;

    /** Tương ứng cột {@code price} — giá thực tế đã bán, số nguyên VNĐ, backend tra lại từ DB (§C.1). */
    @Column(nullable = false)
    @Comment("Gia thuc te da ban mot don vi, so nguyen VND")
    private Long price;

    /** Tương ứng cột {@code original_price} — giá gốc, dùng hiển thị gạch ngang. */
    @Column(nullable = false)
    @Comment("Gia goc mot don vi de hien thi gach ngang, so nguyen VND")
    private Long originalPrice;

    /** Tương ứng cột {@code quantity}. */
    @Column(nullable = false)
    @Comment("So luong dat")
    private Integer quantity;
}
