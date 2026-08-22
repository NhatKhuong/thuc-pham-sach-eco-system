package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.Brand;

import java.util.Optional;

/**
 * PORT của {@code Brand} ở <b>mức tối thiểu để gán quan hệ</b> {@code product.brand_id}.
 * <p>
 * Cùng lý do tồn tại với {@link CategoryRepository}. Khác một điểm: {@code brandId} là nullable
 * (khớp {@code brandId: number | null} của client), nên gọi hay không là quyết định của tầng trên.
 */
public interface BrandRepository {

    /**
     * @param id khóa chính của thương hiệu
     * @return thương hiệu, hoặc rỗng khi id không tồn tại
     */
    Optional<Brand> findById(Long id);
}
