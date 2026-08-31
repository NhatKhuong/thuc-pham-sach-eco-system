package com.nss.ddd.application.service.category.impl;

import com.nss.ddd.application.mapper.CategoryMapper;
import com.nss.ddd.application.model.response.CategoryResponse;
import com.nss.ddd.application.service.category.CategoryAppService;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.service.CategoryDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Hiện thực use case đọc danh mục.
 * <p>
 * Tầng này chỉ điều phối: hỏi domain, rồi lắp kết quả thành kiểu của bề mặt dây. Không có quy tắc
 * nghiệp vụ nào nằm ở đây.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryAppServiceImpl implements CategoryAppService {

    private final CategoryDomainService categoryDomainService;

    @Override
    public List<CategoryResponse> findAll() {
        List<Category> categories = categoryDomainService.findAll();
        log.info("findAll: success | count={}", categories.size());
        return toResponseList(categories);
    }

    @Override
    public List<CategoryResponse> findRootCategories() {
        List<Category> categories = categoryDomainService.findRootCategories();
        log.info("findRootCategories: success | count={}", categories.size());
        return toResponseList(categories);
    }

    @Override
    public CategoryResponse findBySlug(String slug) {
        Category category = categoryDomainService.findBySlug(slug);
        if (category == null) {
            log.warn("findBySlug: not found | slug={}", slug);
            return null;
        }
        log.info("findBySlug: success | categoryId={} slug={}", category.getId(), slug);
        return CategoryMapper.toResponse(category, categoryDomainService.countProducts(category));
    }

    /**
     * Lắp một danh sách entity thành response, kèm {@code productCount} cho mỗi danh mục.
     * <p>
     * <b>Một lượt đếm cho mỗi danh mục (N truy vấn cho N danh mục).</b> Chấp nhận được vì số danh
     * mục nhỏ và không phân trang — khác hẳn đường đọc sản phẩm, nơi N+1 ảnh mới là thứ phải tránh
     * ({@code ProductAppServiceImpl#toPaginatedResponse}). Gộp thành một truy vấn {@code GROUP BY}
     * là việc tối ưu của một ticket sau nếu số danh mục lớn lên.
     *
     * @param categories entity cần lắp
     * @return response đã kèm {@code productCount}
     */
    private List<CategoryResponse> toResponseList(List<Category> categories) {
        List<CategoryResponse> responses = new ArrayList<>(categories.size());
        for (Category category : categories) {
            responses.add(CategoryMapper.toResponse(category, categoryDomainService.countProducts(category)));
        }
        return responses;
    }
}
