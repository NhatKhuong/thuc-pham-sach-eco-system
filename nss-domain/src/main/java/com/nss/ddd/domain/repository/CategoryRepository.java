package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.Category;

import java.util.Optional;

/**
 * PORT của {@code Category} ở <b>mức tối thiểu để gán quan hệ</b> {@code product.category_id}.
 * <p>
 * Cố ý chỉ có đúng một method: aggregate {@code Category} là việc của ticket riêng, ở đây chỉ cần
 * đủ để {@code POST} / {@code PUT} sản phẩm phân giải được {@code categoryId} thành entity, và để
 * {@code categoryId} sai trả về lỗi nghiệp vụ thay vì lỗi ràng buộc khóa ngoại ở tầng dưới.
 */
public interface CategoryRepository {

    /**
     * @param id khóa chính của danh mục
     * @return danh mục, hoặc rỗng khi id không tồn tại
     */
    Optional<Category> findById(Long id);
}
