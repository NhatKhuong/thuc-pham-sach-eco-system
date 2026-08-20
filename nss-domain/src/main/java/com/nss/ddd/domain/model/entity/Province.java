package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

/**
 * Tỉnh / thành phố — dữ liệu tra cứu cho ô {@code <Select>} địa giới hành chính.
 * <p>
 * {@code code} là <b>natural key</b>: {@code types/location.ts#Province} chỉ có
 * {@code code} và {@code name}, và ô {@code <Select>} phía client chạy theo mã (§D #3).
 * Thêm một cột {@code id} tự tăng ở đây sẽ tạo ra một danh tính thứ hai mà không ai dùng.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Comment("Tinh/thanh pho; code la natural key")
public class Province {

    /** Tương ứng cột {@code code} — natural key, không {@code @GeneratedValue}. */
    @Id
    @Column(nullable = false, length = 16)
    @Comment("Ma tinh/thanh, khoa chinh tu nhien")
    private String code;

    /** Tương ứng cột {@code name}. */
    @Column(nullable = false, length = 128)
    @Comment("Ten tinh/thanh")
    private String name;
}
