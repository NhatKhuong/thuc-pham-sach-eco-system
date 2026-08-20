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

import java.time.LocalDateTime;

/**
 * Một địa chỉ trong sổ địa chỉ của người dùng.
 * <p>
 * <b>Giữ cả mã lẫn tên</b> ({@code province_code} + {@code province},
 * {@code district_code} + {@code district}) là chủ ý, không phải trùng lặp — §D #3:
 * ô {@code <Select>} chọn địa giới hành chính chạy theo <b>mã</b>, còn
 * {@link ShippingInfo} của đơn hàng lưu <b>tên</b>. Chỉ lưu tên thì mỗi lần mở lại form
 * phải tra ngược tên → mã, và cơ chế đó vỡ ngay khi tên đơn vị hành chính thay đổi.
 * <p>
 * Bốn cột này là <b>chuỗi trần, cố ý không có khóa ngoại</b> về {@link Province} /
 * {@link District}: chúng là bản chụp tại thời điểm lưu, phải giữ nguyên khi bảng địa giới
 * được cập nhật. {@code ward} chỉ có tên vì §B.9 nói {@code getWards} trả {@code string[]}.
 * <p>
 * §B.5 bắt <b>mỗi user chỉ một địa chỉ mặc định</b>. MySQL không có unique index có điều kiện,
 * nên ràng buộc đó do tầng service giữ trong cùng một transaction — không có gì ở schema chặn được.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(indexes = @Index(name = "idx_user_id", columnList = "user_id"))
@Comment("So dia chi cua nguoi dung")
public class Address {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code user_id} — §C.4: mọi endpoint {@code /addresses} lọc theo user trong JWT. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_address_user"))
    @Comment("Chu so huu dia chi")
    private User user;

    /** Tương ứng cột {@code full_name} — người nhận hàng, có thể khác chủ tài khoản. */
    @Column(nullable = false, length = 128)
    @Comment("Ho ten nguoi nhan hang")
    private String fullName;

    /** Tương ứng cột {@code phone}. */
    @Column(nullable = false, length = 20)
    @Comment("So dien thoai nguoi nhan")
    private String phone;

    /** Tương ứng cột {@code province_code} — mã tỉnh, ô {@code <Select>} chạy theo mã này. */
    @Column(nullable = false, length = 16)
    @Comment("Ma tinh/thanh, ban chup tai thoi diem luu")
    private String provinceCode;

    /** Tương ứng cột {@code province} — tên tỉnh, dùng khi in ra địa chỉ. */
    @Column(nullable = false, length = 128)
    @Comment("Ten tinh/thanh, ban chup tai thoi diem luu")
    private String province;

    /** Tương ứng cột {@code district_code} — mã quận/huyện. */
    @Column(nullable = false, length = 16)
    @Comment("Ma quan/huyen, ban chup tai thoi diem luu")
    private String districtCode;

    /** Tương ứng cột {@code district} — tên quận/huyện. */
    @Column(nullable = false, length = 128)
    @Comment("Ten quan/huyen, ban chup tai thoi diem luu")
    private String district;

    /** Tương ứng cột {@code ward} — <b>chỉ tên</b>, không có mã (§B.9). */
    @Column(nullable = false, length = 128)
    @Comment("Ten phuong/xa; khong luu ma vi contract chi tra ten")
    private String ward;

    /** Tương ứng cột {@code street}. */
    @Column(nullable = false, length = 255)
    @Comment("So nha va ten duong")
    private String street;

    /** Tương ứng cột {@code is_default} — §B.5: mỗi user chỉ một địa chỉ có giá trị {@code true}. */
    @Column(nullable = false)
    @Comment("Co phai dia chi mac dinh cua user hay khong")
    private Boolean isDefault;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem tao, luu theo gio UTC")
    private LocalDateTime createdAt;

    /** Tương ứng cột {@code updated_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Comment("Thoi diem cap nhat gan nhat, luu theo gio UTC")
    private LocalDateTime updatedAt;
}
