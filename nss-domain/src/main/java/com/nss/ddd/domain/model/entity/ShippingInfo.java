package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

/**
 * Thông tin giao hàng của một đơn — {@code @Embeddable}, nằm ngay trong bảng
 * {@code customer_order}, không phải bảng riêng.
 * <p>
 * Đây là <b>bản chụp</b> tại thời điểm đặt hàng, không phải tham chiếu tới {@link Address}:
 * khách vãng lai không có sổ địa chỉ, và đơn cũ phải giữ nguyên địa chỉ đã giao dù người dùng
 * sửa hoặc xóa địa chỉ trong sổ.
 * <p>
 * Chỉ lưu <b>tên</b> tỉnh/quận/phường, khớp {@code types/order.ts#ShippingInfo} — đây là dữ liệu
 * để in lên đơn, không phải để đổ vào ô {@code <Select>}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ShippingInfo {

    /** Tương ứng cột {@code full_name}. */
    @Column(nullable = false, length = 128)
    @Comment("Ho ten nguoi nhan hang")
    private String fullName;

    /** Tương ứng cột {@code phone}. */
    @Column(nullable = false, length = 20)
    @Comment("So dien thoai nguoi nhan")
    private String phone;

    /** Tương ứng cột {@code email} — kênh gửi xác nhận đơn cho khách vãng lai. */
    @Column(nullable = false, length = 160)
    @Comment("Email nhan xac nhan don hang")
    private String email;

    /** Tương ứng cột {@code province} — tên tỉnh/thành. */
    @Column(nullable = false, length = 128)
    @Comment("Ten tinh/thanh giao hang")
    private String province;

    /** Tương ứng cột {@code district} — tên quận/huyện. */
    @Column(nullable = false, length = 128)
    @Comment("Ten quan/huyen giao hang")
    private String district;

    /** Tương ứng cột {@code ward} — tên phường/xã. */
    @Column(nullable = false, length = 128)
    @Comment("Ten phuong/xa giao hang")
    private String ward;

    /** Tương ứng cột {@code street}. */
    @Column(nullable = false, length = 255)
    @Comment("So nha va ten duong giao hang")
    private String street;

    /** Tương ứng cột {@code note} — ghi chú của khách, tùy chọn ({@code note?} phía client). */
    @Column(length = 500)
    @Comment("Ghi chu giao hang cua khach; null neu khong co")
    private String note;
}
