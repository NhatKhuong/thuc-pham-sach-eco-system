package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.Category;

import java.util.List;
import java.util.Optional;

/**
 * PORT của {@code Category}.
 * <p>
 * Ban đầu (backlog 0008) chỉ có {@link #findById(Long)}, đủ để {@code POST}/{@code PUT} sản phẩm
 * phân giải {@code categoryId}. Backlog 0024 phơi §B.2 ra HTTP nên thêm ba method đọc — khái niệm
 * cây danh mục đã có sẵn ở entity từ đầu, ticket này chỉ cần đủ để đọc nó.
 */
public interface CategoryRepository {

    /**
     * @param id khóa chính của danh mục
     * @return danh mục, hoặc rỗng khi id không tồn tại
     */
    Optional<Category> findById(Long id);

    /**
     * Toàn bộ danh mục — nguồn của {@code GET /categories} (API_CONTRACT §B.2).
     *
     * @return mọi danh mục, sắp theo tên
     */
    List<Category> findAll();

    /**
     * Danh mục gốc — nguồn của {@code GET /categories?root=true} (API_CONTRACT §B.2).
     *
     * @return các danh mục có {@code parent = null}, sắp theo tên
     */
    List<Category> findRootCategories();

    /**
     * Tra danh mục bằng slug — khóa tra cứu của {@code GET /categories/{slug}}.
     *
     * @param slug slug không dấu, duy nhất
     * @return danh mục, hoặc rỗng khi slug không tồn tại
     */
    Optional<Category> findBySlug(String slug);
}
