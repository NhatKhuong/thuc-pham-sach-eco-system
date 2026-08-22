package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Lệnh cập nhật sản phẩm — dạng dữ liệu của tầng application, dựng từ {@code UpdateProductRequest} bởi
 * {@code ProductControllerMapper}. Sản phẩm được xác định bằng {@code id} trên đường dẫn,
 * nên {@code id} không nằm trong lệnh này.
 * <p>
 * <b>Chỉ chứa trường người nhập.</b> Những gì server tự tính ({@code nameNormalized},
 * {@code createdAt} / {@code updatedAt}, {@code isActive}) và những gì bị chặn cứng ({@code id},
 * {@code effectivePrice}, {@code rating}, {@code reviewCount}, {@code sold}) cố ý không có mặt —
 * client gửi lên thì Jackson bỏ qua ngay từ tầng request, không cần ai kiểm.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductCommand {

    private String slug;

    private String name;

    private String shortDescription;

    private String description;

    private Long price;

    /** {@code null} nghĩa là không giảm giá. */
    private Long salePrice;

    private String unit;

    private String origin;

    private Integer stock;

    private Boolean isFeatured;

    private Boolean isBestSeller;

    private Long categoryId;

    /** {@code null} khi không gắn thương hiệu. */
    private Long brandId;

    /** Đường dẫn ảnh tương đối; thứ tự trong danh sách là thứ tự hiển thị. */
    private List<String> images;
}
