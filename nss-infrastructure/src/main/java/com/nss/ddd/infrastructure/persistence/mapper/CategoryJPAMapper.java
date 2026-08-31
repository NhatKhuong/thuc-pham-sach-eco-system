package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data interface của {@code category}.
 * <p>
 * Ban đầu (backlog 0008) chỉ cần {@code findById} kế thừa sẵn từ {@link JpaRepository}, đủ để phân
 * giải {@code product.category_id}. Backlog 0024 thêm ba method đọc phục vụ §B.2.
 */
public interface CategoryJPAMapper extends JpaRepository<Category, Long> {

    /**
     * Toàn bộ danh mục, sắp theo tên — nguồn của {@code GET /categories}.
     *
     * @return mọi danh mục, sắp {@code name} tăng dần
     */
    List<Category> findAllByOrderByNameAsc();

    /**
     * Danh mục gốc, sắp theo tên — nguồn của {@code GET /categories?root=true}.
     *
     * @return các danh mục có {@code parent IS NULL}, sắp {@code name} tăng dần
     */
    List<Category> findByParentIsNullOrderByNameAsc();

    /**
     * @param slug slug cần tra
     * @return danh mục, hoặc rỗng khi slug không tồn tại
     */
    Optional<Category> findBySlug(String slug);
}
