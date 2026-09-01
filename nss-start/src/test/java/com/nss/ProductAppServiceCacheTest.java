package com.nss;

import com.nss.ddd.application.model.command.UpdateProductCommand;
import com.nss.ddd.application.model.response.ProductMutationResponse;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.application.service.product.ProductAppService;
import com.nss.ddd.application.service.product.cache.ProductCacheService;
import com.nss.ddd.application.service.product.impl.ProductAppServiceImpl;
import com.nss.ddd.domain.model.entity.Brand;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.service.ProductDomainService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đường evict cache của {@code ProductAppServiceImpl} (backlog 0035 Phase 1) — {@code findProductBySlug}
 * đi qua {@link ProductCacheService}, {@code updateProduct} evict cả slug cũ lẫn slug mới nếu đổi,
 * {@code deleteProduct} đọc sản phẩm trước khi xoá mềm để có slug mà evict.
 */
class ProductAppServiceCacheTest {

    private final ProductDomainService productDomainService = mock(ProductDomainService.class);

    private final ProductCacheService productCacheService = mock(ProductCacheService.class);

    private final ProductAppService productAppService =
            new ProductAppServiceImpl(productDomainService, productCacheService);

    @Test
    @DisplayName("findProductBySlug uy thac cho ProductCacheService, tra thang ket qua")
    void findProductBySlugDelegatesToCacheService() {
        ProductResponse cached = new ProductResponse().setId(1L).setSlug("ca-rot");
        when(productCacheService.getProductBySlug("ca-rot")).thenReturn(cached);

        ProductResponse result = productAppService.findProductBySlug("ca-rot");

        assertTrue(result == cached);
    }

    @Test
    @DisplayName("findProductBySlug: cache tra null -> null (khong tu y tra ve gia tri khac)")
    void findProductBySlugReturnsNullWhenCacheMisses() {
        when(productCacheService.getProductBySlug("khong-ton-tai")).thenReturn(null);

        ProductResponse result = productAppService.findProductBySlug("khong-ton-tai");

        assertNull(result);
    }

    @Test
    @DisplayName("updateProduct GIU NGUYEN slug -> chi evict DUNG MOT LAN (khong evict 'slug moi' trung lap)")
    void updateProductKeepingSlugEvictsOnce() {
        Product existing = genExistingProduct("ca-rot");
        when(productDomainService.findById(1L)).thenReturn(existing);
        when(productDomainService.genSlug(eq("ca-rot"), any())).thenReturn("ca-rot");
        when(productDomainService.hasValidSalePrice(any(), any())).thenReturn(true);
        when(productDomainService.findCategoryById(10L)).thenReturn(new Category().setId(10L));
        Product saved = genExistingProduct("ca-rot");
        when(productDomainService.update(any(), any(), any())).thenReturn(saved);
        when(productDomainService.replaceImages(any(), any())).thenReturn(List.of());

        UpdateProductCommand command = genUpdateCommand("ca-rot", 10L);
        ProductMutationResponse result = productAppService.updateProduct(1L, command);

        assertTrue(result.getProduct() != null);
        verify(productCacheService, times(1)).evict("ca-rot");
    }

    @Test
    @DisplayName("updateProduct DOI slug -> evict CA slug cu LAN slug moi")
    void updateProductChangingSlugEvictsBothOldAndNew() {
        Product existing = genExistingProduct("ca-rot-cu");
        when(productDomainService.findById(1L)).thenReturn(existing);
        when(productDomainService.genSlug(eq("ca-rot-moi"), any())).thenReturn("ca-rot-moi");
        when(productDomainService.hasSlugTaken("ca-rot-moi")).thenReturn(false);
        when(productDomainService.hasValidSalePrice(any(), any())).thenReturn(true);
        when(productDomainService.findCategoryById(10L)).thenReturn(new Category().setId(10L));
        Product saved = genExistingProduct("ca-rot-moi");
        when(productDomainService.update(any(), any(), any())).thenReturn(saved);
        when(productDomainService.replaceImages(any(), any())).thenReturn(List.of());

        UpdateProductCommand command = genUpdateCommand("ca-rot-moi", 10L);
        productAppService.updateProduct(1L, command);

        verify(productCacheService, times(1)).evict("ca-rot-cu");
        verify(productCacheService, times(1)).evict("ca-rot-moi");
    }

    @Test
    @DisplayName("deleteProduct thanh cong -> doc san pham TRUOC de lay slug, roi evict dung slug do")
    void deleteProductEvictsUsingSlugReadBeforeSoftDelete() {
        Product existing = genExistingProduct("ca-rot");
        when(productDomainService.findById(5L)).thenReturn(existing);
        when(productDomainService.softDelete(5L)).thenReturn(true);

        boolean deleted = productAppService.deleteProduct(5L);

        assertTrue(deleted);
        verify(productCacheService, times(1)).evict("ca-rot");
    }

    @Test
    @DisplayName("deleteProduct: san pham khong ton tai -> false, KHONG goi evict")
    void deleteProductNotFoundDoesNotEvict() {
        when(productDomainService.findById(99L)).thenReturn(null);

        boolean deleted = productAppService.deleteProduct(99L);

        assertFalse(deleted);
        verify(productCacheService, never()).evict(any());
        verify(productDomainService, never()).softDelete(anyLong());
    }

    private Product genExistingProduct(String slug) {
        return new Product().setId(1L).setSlug(slug).setName("Cà rốt").setPrice(20_000L)
                .setUnit("kg").setStock(10).setSold(0)
                .setRating(java.math.BigDecimal.ZERO).setReviewCount(0)
                .setIsFeatured(false).setIsBestSeller(false)
                .setCategory(new Category().setId(10L)).setBrand(new Brand().setId(1L));
    }

    private UpdateProductCommand genUpdateCommand(String slug, Long categoryId) {
        return new UpdateProductCommand()
                .setSlug(slug)
                .setName("Cà rốt")
                .setPrice(20_000L)
                .setUnit("kg")
                .setStock(10)
                .setCategoryId(categoryId)
                .setBrandId(null)
                .setImages(List.of());
    }
}
