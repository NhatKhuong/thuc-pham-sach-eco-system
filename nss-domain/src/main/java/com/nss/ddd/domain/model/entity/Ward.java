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
 * Phường / xã.
 * <p>
 * Là bảng thật <b>dù §B.9 chỉ trả {@code string[]}</b>: API trả ra dạng gì là chuyện của tầng DTO,
 * còn dữ liệu thì vẫn phải có chỗ ở. {@code types/location.ts} cố ý không có interface
 * {@code Ward} vì {@code Address.ward} và {@code ShippingInfo.ward} đều lưu tên — điều đó nói về
 * shape của response, không nói rằng phường/xã không tồn tại.
 * <p>
 * Khóa chính là {@code id} tự tăng chứ không phải natural key: contract không hề đưa ra mã phường,
 * nên ở đây không có mã nào để làm danh tính.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(indexes = @Index(name = "idx_district_code", columnList = "district_code"))
@Comment("Phuong/xa")
public class Ward {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code district_code} — khóa ngoại về {@link District#getCode()}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_ward_district"))
    @Comment("Quan/huyen chua phuong/xa nay")
    private District district;

    /** Tương ứng cột {@code name} — giá trị duy nhất mà {@code getWards} trả ra (§B.9). */
    @Column(nullable = false, length = 128)
    @Comment("Ten phuong/xa")
    private String name;
}
