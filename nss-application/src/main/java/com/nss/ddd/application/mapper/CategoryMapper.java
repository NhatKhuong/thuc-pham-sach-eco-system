package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.response.CategoryResponse;
import com.nss.ddd.domain.model.entity.Category;

/**
 * Converter viết tay giữa {@code Category} và {@code CategoryResponse}.
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard
 * (coding-conventions §7).
 */
public final class CategoryMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private CategoryMapper() {
    }

    /**
     * @param category entity; {@code parent} không cần nạp sẵn — chỉ đọc {@code id} qua proxy LAZY,
     *                 một cột vô hướng nằm sẵn trên chính dòng {@code category}
     * @param productCount số sản phẩm còn hiệu lực thuộc danh mục này (đã tính sẵn, xem
     *                     {@code CategoryDomainService#countProducts})
     * @return payload, hoặc {@code null} khi {@code category} rỗng
     */
    public static CategoryResponse toResponse(Category category, long productCount) {
        if (category == null) {
            return null;
        }
        return new CategoryResponse()
                .setId(category.getId())
                .setSlug(category.getSlug())
                .setName(category.getName())
                .setDescription(category.getDescription())
                .setImage(category.getImage())
                .setParentId(category.getParent() == null ? null : category.getParent().getId())
                .setProductCount(productCount);
    }
}
