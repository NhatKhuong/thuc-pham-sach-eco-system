package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * Đơn hàng.
 * <p>
 * Bảng tên {@code customer_order} chứ không phải {@code order}: {@code ORDER} là từ khóa dành
 * riêng của MySQL. {@code architecture} §3 cho phép tên bảng lệch tên entity, với điều kiện
 * khai {@code @Table(name = ...)} — đúng như ở đây.
 * <p>
 * <b>Mọi con số tiền trong bảng này do backend tự tính</b>, không lấy từ payload của client (§C.1).
 * {@code CreateOrderPayload.items} mang theo {@code price}/{@code originalPrice}/{@code stock},
 * nhưng đó là bản chụp phía giỏ hàng — tin nó là để người ta tự đặt giá cho mình.
 * <p>
 * <b>Không dùng pattern bảng-theo-tháng</b> của {@code architecture} §3: pattern đó bắt mã đơn
 * phải chứa {@code System.currentTimeMillis()} để suy ngược ra tháng, còn §B.6 đã pin mã đơn
 * dạng {@code NSS-YYYYMMDD-XXXXXXXXXX}. Xem ADR 0002.
 * <p>
 * <b>Phần đuôi là ngẫu nhiên an toàn mật mã chứ không phải mốc thời gian</b> (ADR 0006) — nên nó
 * cũng không suy ngược ra tháng được, và kết luận trên càng đúng hơn chứ không yếu đi.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "customer_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_created_at", columnList = "created_at"),
                // Bo loc `q` cua GET /admin/orders so khop tren cot DA BO DAU cua nguoi nhan
                // (§B.12.2). Cot nam trong ShippingInfo @Embeddable nhung index thi phai khai
                // o @Table cua entity chu — @Embeddable khong khai index duoc.
                @Index(name = "idx_full_name_normalized", columnList = "full_name_normalized")
        }
)
@Comment("Don hang; ten bang lech ten entity vi ORDER la tu khoa MySQL")
public class Order {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /**
     * Tương ứng cột {@code code} — mã đơn hiển thị cho khách, dạng
     * {@code NSS-20260826-K7M2QX9P4T} (§B.6, <b>ADR 0006</b>).
     * <p>
     * {@code GET /orders/{code}} là endpoint <b>công khai</b> và là lối duy nhất để khách vãng lai
     * xem lại đơn, nên mã <b>phải khó đoán</b>: 10 ký tự cuối sinh bằng {@code SecureRandom},
     * không gian 32^10 ≈ 1,13 × 10^15. Dạng tuần tự {@code NNNN} trước đây cho phép dò
     * {@code 0001..9999} mỗi ngày để rút cả 8 trường PII của khối {@code shipping} — xem
     * {@code bugs/0004}.
     * <p>
     * <b>Đơn cũ giữ mã tuần tự cũ</b> (không backfill, Owner chốt 2026-08-26), nên cột này chứa
     * <i>cả hai</i> dạng và không có phép kiểm khuôn dạng nào ở đường đọc.
     * <p>
     * {@code length = 32} <b>không đổi</b> theo ticket này: mã mới dài 23 ký tự.
     */
    @Column(nullable = false, length = 32)
    @Comment("Ma don hien thi cho khach, duy nhat, vi du NSS-20260826-K7M2QX9P4T")
    private String code;

    /**
     * Tương ứng cột {@code user_id} — <b>nullable</b>, {@code null} là đơn của khách vãng lai (§B.6, §D #2).
     * <p>
     * Giá trị này backend lấy từ JWT, <b>client không bao giờ gửi lên</b> (§C.2).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_customer_order_user"))
    @Comment("Chu don; null la don khach vang lai")
    private User user;

    /** Thông tin giao hàng, nhúng thẳng vào bảng này — xem {@link ShippingInfo}. */
    @Embedded
    private ShippingInfo shipping;

    /**
     * Tương ứng cột {@code payment_method}.
     * <p>
     * {@code // 0=COD, 1=BANK_TRANSFER, 2=MOMO, 3=VNPAY}
     * <p>
     * Việc map số này sang chuỗi {@code 'cod'} / {@code 'bank_transfer'} / {@code 'momo'} /
     * {@code 'vnpay'} của client là của tầng DTO.
     */
    @Column(nullable = false)
    @Comment("Phuong thuc thanh toan: 0=COD, 1=BANK_TRANSFER, 2=MOMO, 3=VNPAY")
    private Integer paymentMethod;

    /**
     * Tương ứng cột {@code status}.
     * <p>
     * {@code // 0=PENDING, 1=CONFIRMED, 2=SHIPPING, 3=DELIVERED, 4=CANCELLED}
     * <p>
     * Cột này chỉ cho biết đơn <b>đang</b> ở đâu; lịch sử đi qua đâu nằm ở {@link OrderStatusHistory}.
     */
    @Column(nullable = false)
    @Comment("Trang thai don: 0=PENDING, 1=CONFIRMED, 2=SHIPPING, 3=DELIVERED, 4=CANCELLED")
    private Integer status;

    /** Tương ứng cột {@code subtotal} — số nguyên VNĐ, backend tự tính từ giá tra lại trong DB (§C.1). */
    @Column(nullable = false)
    @Comment("Tong tien hang truoc giam gia, so nguyen VND")
    private Long subtotal;

    /** Tương ứng cột {@code discount} — backend tự xác thực lại mã giảm giá theo {@code subtotal} vừa tính (§C.1). */
    @Column(nullable = false)
    @Comment("So tien duoc giam, so nguyen VND")
    private Long discount;

    /** Tương ứng cột {@code shipping_fee} — backend tự tính; {@code calcShippingFee()} phía client chỉ là ước tính hiển thị. */
    @Column(nullable = false)
    @Comment("Phi van chuyen, so nguyen VND")
    private Long shippingFee;

    /** Tương ứng cột {@code total} — backend tự tính, là con số cuối cùng frontend hiển thị lại. */
    @Column(nullable = false)
    @Comment("Tong tien phai tra, so nguyen VND")
    private Long total;

    /**
     * Tương ứng cột {@code coupon_code} — {@code null} nếu không áp mã.
     * <p>
     * <b>Chuỗi trần, cố ý không có khóa ngoại</b> về {@link Coupon}: đây là bản chụp mã đã dùng,
     * phải đọc được cả khi mã bị xóa hoặc đổi điều kiện sau này.
     */
    @Column(length = 32)
    @Comment("Ma giam gia da ap, ban chup; null neu khong ap ma")
    private String couponCode;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem dat don, luu theo gio UTC")
    private LocalDateTime createdAt;

    /** Tương ứng cột {@code updated_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Comment("Thoi diem cap nhat gan nhat, luu theo gio UTC")
    private LocalDateTime updatedAt;
}
