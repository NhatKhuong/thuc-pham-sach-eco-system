package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.entity.Category;

import java.util.List;

/**
 * Domain service của aggregate {@code Category} — API_CONTRACT §B.2.
 * <p>
 * Chỉ biết port ({@code CategoryRepository}, {@code ProductRepository}), không biết adapter nào
 * đang được nối vào.
 */
public interface CategoryDomainService {

    /**
     * @return mọi danh mục, sắp theo tên
     */
    List<Category> findAll();

    /**
     * @return các danh mục gốc, sắp theo tên
     */
    List<Category> findRootCategories();

    /**
     * @param slug slug cần tra
     * @return danh mục, hoặc {@code null} khi không tồn tại
     */
    Category findBySlug(String slug);

    /**
     * Số sản phẩm còn hiệu lực thuộc một danh mục — {@code Category.productCount} của §B.2.
     * <p>
     * <b>Với danh mục gốc, con số này gồm cả sản phẩm của danh mục con một cấp</b>, đúng câu chốt
     * của §B.2: "sidebar bộ lọc hiển thị đúng như vậy". Cách giữ đúng điều đó <i>theo cấu tạo</i>
     * chứ không theo may mắn: dùng lại <b>đúng</b> mệnh đề lọc danh mục mà
     * {@code GET /admin/products?category=} và {@code GET /products?category=} đã dùng
     * ({@code ProductRepository#countAdminProducts} — mệnh đề {@code c.slug = :slug OR cp.slug = :slug}),
     * thay vì tính một bảng con riêng có thể lệch với hai chỗ kia (coding-conventions §15: một con
     * số, một nguồn).
     *
     * @param category danh mục cần đếm
     * @return số sản phẩm còn hiệu lực thuộc danh mục này hoặc danh mục con một cấp của nó
     */
    long countProducts(Category category);
}
