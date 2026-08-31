package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.repository.CategoryRepository;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.service.CategoryDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Hiện thực domain service của {@code Category}.
 * <p>
 * Phụ thuộc {@code CategoryRepository} và {@code ProductRepository} — cả hai là port, không có tham
 * chiếu nào tới module infrastructure ở compile-time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryDomainServiceImpl implements CategoryDomainService {

    /** {@code countAdminProducts} bỏ qua {@code page}/{@code limit}; truyền giá trị trung tính. */
    private static final int NEUTRAL_PAGE = 1;

    private static final int NEUTRAL_LIMIT = 1;

    private final CategoryRepository categoryRepository;

    private final ProductRepository productRepository;

    @Override
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @Override
    public List<Category> findRootCategories() {
        return categoryRepository.findRootCategories();
    }

    @Override
    public Category findBySlug(String slug) {
        return categoryRepository.findBySlug(slug).orElse(null);
    }

    @Override
    public long countProducts(Category category) {
        if (category == null) {
            return 0L;
        }
        // Dung lai DUNG menh de loc danh muc cua GET /admin/products (mot cap con) — xem javadoc
        // interface. q/stockStatus/sort deu null: chi loc theo category.
        return productRepository.countAdminProducts(
                ProductFilter.of(null, category.getSlug(), null, null, NEUTRAL_PAGE, NEUTRAL_LIMIT));
    }
}
