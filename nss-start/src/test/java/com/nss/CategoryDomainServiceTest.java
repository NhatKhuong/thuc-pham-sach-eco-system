package com.nss;

import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.repository.CategoryRepository;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.service.impl.CategoryDomainServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm {@code CategoryDomainServiceImpl} (backlog 0024 §B.2) — trọng tâm là {@code countProducts},
 * chỗ duy nhất mang logic thật của class này (bốn method còn lại chỉ điều phối trần).
 * <p>
 * <b>{@code countProducts} phải dùng lại ĐÚNG mệnh đề lọc danh mục của {@code GET /admin/products}</b>
 * (coding-conventions §15: một con số, một nguồn) — ca dưới đây khoá lại bằng cách bắt đúng
 * {@link ProductFilter} mà nó truyền xuống {@code ProductRepository#countAdminProducts}: slug đúng
 * bằng slug của danh mục, mọi tiêu chí khác đều {@code null} (không lọc thêm gì).
 */
@ExtendWith(MockitoExtension.class)
class CategoryDomainServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryDomainServiceImpl categoryDomainService;

    @Test
    @DisplayName("countProducts dung lai dung menh de loc danh muc cua GET /admin/products")
    void countProductsReusesAdminCategoryFilter() {
        Category category = new Category().setId(1L).setSlug("rau-cu").setName("Rau củ");
        when(productRepository.countAdminProducts(any())).thenReturn(7L);

        long count = categoryDomainService.countProducts(category);

        assertEquals(7L, count);
        ArgumentCaptor<ProductFilter> captor = ArgumentCaptor.forClass(ProductFilter.class);
        verify(productRepository).countAdminProducts(captor.capture());
        ProductFilter filter = captor.getValue();
        assertEquals("rau-cu", filter.getCategorySlug());
        assertNull(filter.getKeyword(), "khong duoc loc them theo tu khoa");
        assertNull(filter.getStockStatus(), "khong duoc loc them theo ton kho");
        assertNull(filter.getSort(), "sort khong tham gia phep dem");
    }

    @Test
    @DisplayName("countProducts voi category null tra 0, khong goi repository")
    void countProductsWithNullCategoryReturnsZero() {
        assertEquals(0L, categoryDomainService.countProducts(null));
    }

    @Test
    @DisplayName("findAll uy thac thang cho CategoryRepository")
    void findAllDelegatesToRepository() {
        List<Category> categories = List.of(new Category().setId(1L));
        when(categoryRepository.findAll()).thenReturn(categories);

        assertEquals(categories, categoryDomainService.findAll());
    }

    @Test
    @DisplayName("findRootCategories uy thac thang cho CategoryRepository")
    void findRootCategoriesDelegatesToRepository() {
        List<Category> roots = List.of(new Category().setId(1L));
        when(categoryRepository.findRootCategories()).thenReturn(roots);

        assertEquals(roots, categoryDomainService.findRootCategories());
    }

    @Test
    @DisplayName("findBySlug tra null khi khong tim thay, khong nem loi")
    void findBySlugReturnsNullWhenNotFound() {
        when(categoryRepository.findBySlug("khong-ton-tai")).thenReturn(Optional.empty());

        assertNull(categoryDomainService.findBySlug("khong-ton-tai"));
    }
}
