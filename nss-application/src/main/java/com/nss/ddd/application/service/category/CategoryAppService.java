package com.nss.ddd.application.service.category;

import com.nss.ddd.application.model.response.CategoryResponse;

import java.util.List;

/**
 * Use case đọc danh mục — điều phối giữa domain service và kiểu của bề mặt dây (API_CONTRACT §B.2).
 * <p>
 * Không có quy tắc nghiệp vụ nào ở đây: quy tắc sống trong {@code CategoryDomainService}, tầng này
 * chỉ hỏi domain rồi lắp kết quả thành response.
 */
public interface CategoryAppService {

    /**
     * @return mọi danh mục, sắp theo tên
     */
    List<CategoryResponse> findAll();

    /**
     * @return các danh mục gốc, sắp theo tên
     */
    List<CategoryResponse> findRootCategories();

    /**
     * @param slug slug của danh mục
     * @return danh mục, hoặc {@code null} khi không tồn tại
     */
    CategoryResponse findBySlug(String slug);
}
