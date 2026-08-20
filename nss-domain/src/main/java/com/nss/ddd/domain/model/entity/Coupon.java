package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Mã giảm giá.
 * <p>
 * {@code code} là <b>natural key</b>: {@code coding-conventions.md} §6 cho phép {@code @Id} trần
 * khi khóa đã có ý nghĩa nghiệp vụ. Client cũng chỉ lưu chuỗi mã trong giỏ hàng chứ không lưu cả
 * object, và xác thực lại mỗi lần giá trị đơn thay đổi (§B.7) — nên mã chính là danh tính.
 * <p>
 * Bốn cột {@code is_active} / {@code starts_at} / {@code ends_at} / {@code usage_limit} +
 * {@code used_count} <b>không có trong {@code types/cart.ts#Coupon}</b> và đó là chủ ý: chúng là
 * dữ liệu nội bộ để {@code GET /coupons/active} biết mã nào còn hiệu lực. Tầng DTO chỉ trả ra
 * {@code code}, {@code type}, {@code value}, {@code minOrderValue}, {@code description}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Comment("Ma giam gia; code la natural key")
public class Coupon {

    /** Tương ứng cột {@code code} — natural key, không {@code @GeneratedValue}. */
    @Id
    @Column(nullable = false, length = 32)
    @Comment("Ma giam gia, khoa chinh tu nhien")
    private String code;

    /**
     * Tương ứng cột {@code type}.
     * <p>
     * {@code // 0=PERCENT, 1=FIXED}
     * <p>
     * Map sang chuỗi {@code 'percent'} / {@code 'fixed'} của client là việc của tầng DTO.
     */
    @Column(nullable = false)
    @Comment("Kieu giam gia: 0=PERCENT, 1=FIXED")
    private Integer type;

    /** Tương ứng cột {@code value} — phần trăm khi {@code type=0}, số nguyên VNĐ khi {@code type=1}. */
    @Column(nullable = false)
    @Comment("Gia tri giam: phan tram neu type=0, so nguyen VND neu type=1")
    private Long value;

    /** Tương ứng cột {@code min_order_value} — §B.7 trả 422 khi {@code subtotal} chưa đạt ngưỡng này. */
    @Column(nullable = false)
    @Comment("Gia tri don toi thieu de dung ma, so nguyen VND")
    private Long minOrderValue;

    /** Tương ứng cột {@code description} — chuỗi hiển thị cho người dùng. */
    @Column(nullable = false, length = 255)
    @Comment("Mo ta ma giam gia hien thi cho nguoi dung")
    private String description;

    /** Tương ứng cột {@code is_active} — cờ bật/tắt thủ công, độc lập với khoảng thời gian hiệu lực. */
    @Column(nullable = false)
    @Comment("Ma co dang duoc bat hay khong")
    private Boolean isActive;

    /** Tương ứng cột {@code starts_at} — <b>lưu giờ UTC</b>; {@code null} nghĩa là không giới hạn đầu. */
    @Comment("Thoi diem bat dau hieu luc, gio UTC; null la khong gioi han")
    private LocalDateTime startsAt;

    /** Tương ứng cột {@code ends_at} — <b>lưu giờ UTC</b>; {@code null} nghĩa là không giới hạn cuối. */
    @Comment("Thoi diem het hieu luc, gio UTC; null la khong gioi han")
    private LocalDateTime endsAt;

    /** Tương ứng cột {@code usage_limit} — tổng số lượt được dùng; {@code null} là không giới hạn. */
    @Comment("Tong so luot duoc dung; null la khong gioi han")
    private Integer usageLimit;

    /** Tương ứng cột {@code used_count} — số lượt đã dùng, so với {@code usage_limit}. */
    @Column(nullable = false)
    @Comment("So luot da dung")
    private Integer usedCount;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem tao, luu theo gio UTC")
    private LocalDateTime createdAt;

    /** Tương ứng cột {@code updated_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Comment("Thoi diem cap nhat gan nhat, luu theo gio UTC")
    private LocalDateTime updatedAt;
}
