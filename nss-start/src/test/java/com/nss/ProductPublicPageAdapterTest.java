package com.nss;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.PriceRange;
import com.nss.ddd.domain.model.ProductSort;
import com.nss.ddd.domain.model.PublicProductFilter;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;
import com.nss.ddd.infrastructure.persistence.repository.ProductRepositoryImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm bốn phép dịch mới của {@code ProductRepositoryImpl} phục vụ §B.1 công khai (backlog 0024):
 * {@code findPublicPage}, {@code findRelated}, {@code findSuggestions}, {@code findPriceRange}.
 * <p>
 * <b>Không lặp lại các ca đã kiểm ở {@code ProductAdminPageAdapterTest}</b> (khoá phụ theo id, escape
 * ký tự đại diện, ánh xạ {@code sort} sang cột) — {@code findPublicPage} dùng lại đúng
 * {@code toSort}/{@code genLikePattern}, nên các ca đó đã được khoá ở file kia. File này chỉ kiểm
 * phần MỚI: tám tham số riêng của bộ lọc công khai đi xuống đúng vị trí, và ba method còn lại dựng
 * đúng {@code Pageable}/tham số.
 */
@ExtendWith(MockitoExtension.class)
class ProductPublicPageAdapterTest {

    @Mock
    private ProductJPAMapper productJPAMapper;

    @InjectMocks
    private ProductRepositoryImpl productRepository;

    @Test
    @DisplayName("findPublicPage truyen dung tam tham so loc rieng cua trang cua hang")
    void passesAllPublicOnlyFilterParameters() {
        when(productJPAMapper.findPublicPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        productRepository.findPublicPage(PublicProductFilter.of(
                "ca rot", "rau-cu", 10000L, 500000L, new BigDecimal("4.0"),
                true, true, false, null, ProductSort.PRICE_ASC, 1, 12));

        verify(productJPAMapper).findPublicPage(
                eq("%ca rot%"), eq("rau-cu"), eq(10000L), eq(500000L), eq(new BigDecimal("4.0")),
                eq(Boolean.TRUE), eq(Boolean.TRUE), eq(Boolean.FALSE), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("findPublicPage: khong loc gi thi moi tham so rieng deu null/false, page tru 1")
    void nullFilterMeansNoConstraints() {
        when(productJPAMapper.findPublicPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        productRepository.findPublicPage(PublicProductFilter.of(
                null, null, null, null, null, null, null, null, null, null, 1, 12));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productJPAMapper).findPublicPage(
                eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(null), eq(null), eq(null), eq(null), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    @Test
    @DisplayName("findPublicPage: total lay tu totalElements, khong phai so phan tu cua trang")
    void totalComesFromTotalElements() {
        // total (42) phai >= pageSize (12): PageImpl tu dieu chinh total ve offset+content.size()
        // khi content khong rong VA offset+pageSize > total — dung 42 de khong cham bay nay,
        // giong precedent cua ProductAdminPageAdapterTest#totalComesFromTotalElementsNotPageSize.
        Product product = new Product().setId(1L);
        when(productJPAMapper.findPublicPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(product), Pageable.ofSize(12), 42));

        PageResult<Product> result = productRepository.findPublicPage(PublicProductFilter.of(
                null, null, null, null, null, null, null, null, null, null, 1, 12));

        assertEquals(1, result.getItems().size());
        assertEquals(42, result.getTotal());
    }

    // ========== findRelated ==========

    @Test
    @DisplayName("findRelated truyen dung categoryId, excludeId, va limit lam pageSize trang dau")
    void findRelatedPassesCategoryExcludeAndLimit() {
        when(productJPAMapper.findRelated(any(), any(), any())).thenReturn(List.of());

        productRepository.findRelated(9L, 42L, 4);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productJPAMapper).findRelated(eq(9L), eq(42L), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(4, pageableCaptor.getValue().getPageSize());
    }

    // ========== findSuggestions ==========

    @Test
    @DisplayName("findSuggestions boc tu khoa thanh mau LIKE, dung lai genLikePattern")
    void findSuggestionsWrapsKeywordInLikePattern() {
        when(productJPAMapper.findSuggestions(any(), any())).thenReturn(List.of());

        productRepository.findSuggestions("ca rot", 5);

        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productJPAMapper).findSuggestions(patternCaptor.capture(), pageableCaptor.capture());
        assertEquals("%ca rot%", patternCaptor.getValue());
        assertEquals(5, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("findSuggestions: khong tu khoa thi mau la null")
    void findSuggestionsNullKeywordMeansNullPattern() {
        when(productJPAMapper.findSuggestions(any(), any())).thenReturn(List.of());

        productRepository.findSuggestions(null, 5);

        verify(productJPAMapper).findSuggestions(eq(null), any(Pageable.class));
    }

    // ========== findPriceRange ==========

    @Test
    @DisplayName("findPriceRange gop dung hai truy van MIN/MAX thanh mot PriceRange")
    void findPriceRangeCombinesMinAndMax() {
        when(productJPAMapper.findMinEffectivePrice()).thenReturn(15000L);
        when(productJPAMapper.findMaxEffectivePrice()).thenReturn(890000L);

        PriceRange range = productRepository.findPriceRange();

        assertEquals(Long.valueOf(15000L), range.getMin());
        assertEquals(Long.valueOf(890000L), range.getMax());
    }

    @Test
    @DisplayName("findPriceRange: khong co san pham nao thi ca hai bien la null")
    void findPriceRangeNullWhenNoProducts() {
        when(productJPAMapper.findMinEffectivePrice()).thenReturn(null);
        when(productJPAMapper.findMaxEffectivePrice()).thenReturn(null);

        PriceRange range = productRepository.findPriceRange();

        assertNotNull(range);
        assertEquals(null, range.getMin());
        assertEquals(null, range.getMax());
    }
}
