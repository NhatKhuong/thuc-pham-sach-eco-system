package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
 * Quận / huyện — dữ liệu tra cứu cho {@code GET /locations/provinces/{code}/districts}.
 * <p>
 * {@code code} là natural key, cùng lý do như {@link Province}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(indexes = @Index(name = "idx_province_code", columnList = "province_code"))
@Comment("Quan/huyen; code la natural key")
public class District {

    /** Tương ứng cột {@code code} — natural key, không {@code @GeneratedValue}. */
    @Id
    @Column(nullable = false, length = 16)
    @Comment("Ma quan/huyen, khoa chinh tu nhien")
    private String code;

    /** Tương ứng cột {@code province_code} — khóa ngoại về {@link Province#getCode()}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_district_province"))
    @Comment("Tinh/thanh chua quan/huyen nay")
    private Province province;

    /** Tương ứng cột {@code name}. */
    @Column(nullable = false, length = 128)
    @Comment("Ten quan/huyen")
    private String name;
}
