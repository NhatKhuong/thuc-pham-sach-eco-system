package com.nss;

import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ProductImage;
import com.nss.ddd.domain.repository.BrandRepository;
import com.nss.ddd.domain.repository.CategoryRepository;
import com.nss.ddd.domain.repository.ProductImageRepository;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.service.impl.ProductDomainServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm quy tắc nghiệp vụ của {@code ProductDomainServiceImpl} bằng port giả — không Spring context,
 * không database.
 * <p>
 * Những thứ được kiểm ở đây đều thuộc loại <b>hỏng trong im lặng</b>: bỏ dấu sai thì tìm kiếm trượt,
 * cặp giá sai thì hiển thị sai, và mốc thời gian lấy nhầm đồng hồ máy thì lệch 7 tiếng mà không có
 * lỗi nào được ném ra.
 */
@ExtendWith(MockitoExtension.class)
class ProductDomainServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private ProductDomainServiceImpl productDomainService;

    @Test
    @DisplayName("create sinh nameNormalized bo dau va ha chu thuong")
    void createGeneratesNormalizedName() {
        Product saved = captureCreated(new Product().setName("Cải ngọt hữu cơ").setSlug("cai-ngot-huu-co"));

        assertEquals("cai ngot huu co", saved.getNameNormalized());
    }

    @Test
    @DisplayName("create gap ca chu d gach ngang, khong chi gap dau thanh")
    void createFoldsDStrokeToPlainD() {
        Product saved = captureCreated(new Product().setName("Đậu Hà Lan Đà Lạt").setSlug("dau-ha-lan"));

        assertEquals("dau ha lan da lat", saved.getNameNormalized());
    }

    @Test
    @DisplayName("create dat createdAt theo gio UTC chu khong phai gio may")
    void createStampsUtcNotLocalTime() {
        // Cat ve MICROS giong domain service: moc no dat bi cat nen co the som hon dong ho vai tram nano
        LocalDateTime beforeUtc = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

        Product saved = captureCreated(new Product().setName("Cà rốt").setSlug("ca-rot"));

        LocalDateTime afterUtc = LocalDateTime.now(ZoneOffset.UTC);
        // Tren may mui gio UTC+7, LocalDateTime.now() roi cach afterUtc bay tieng nen khang dinh
        // isAfter duoi day se do — do chinh la phep kiem "khong duoc dung now()".
        assertFalse(saved.getCreatedAt().isBefore(beforeUtc), "createdAt som hon gio UTC hien tai");
        assertFalse(saved.getCreatedAt().isAfter(afterUtc), "createdAt muon hon gio UTC hien tai");
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt(), "ban ghi moi thi hai moc bang nhau");
    }

    @Test
    @DisplayName("create dat isActive true va cac cot thong ke ve moc 0")
    void createSetsServerOwnedDefaults() {
        Product saved = captureCreated(new Product().setName("Cà rốt").setSlug("ca-rot"));

        assertEquals(Boolean.TRUE, saved.getIsActive());
        assertEquals(new BigDecimal("0.0"), saved.getRating());
        assertEquals(0, saved.getReviewCount());
        assertEquals(0, saved.getSold());
        assertEquals(Boolean.FALSE, saved.getIsFeatured());
        assertEquals(Boolean.FALSE, saved.getIsBestSeller());
    }

    @Test
    @DisplayName("salePrice rong la hop le, bang hoac lon hon price thi khong")
    void salePriceRuleRejectsOnlyNonDiscounts() {
        assertTrue(productDomainService.hasValidSalePrice(45000L, null));
        assertTrue(productDomainService.hasValidSalePrice(45000L, 39000L));
        assertFalse(productDomainService.hasValidSalePrice(45000L, 45000L));
        assertFalse(productDomainService.hasValidSalePrice(45000L, 50000L));
    }

    @Test
    @DisplayName("replaceImages danh sortOrder theo dung vi tri client gui len")
    void replaceImagesNumbersBySubmittedOrder() {
        Product product = new Product().setId(9L);

        productDomainService.replaceImages(product, List.of("/images/a.jpg", "/images/b.jpg"));

        verify(productImageRepository).deleteByProductId(9L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(productImageRepository).saveAll(captor.capture());
        List<ProductImage> images = captor.getValue();
        assertEquals(2, images.size());
        assertEquals("/images/a.jpg", images.get(0).getUrl());
        assertEquals(0, images.get(0).getSortOrder());
        assertEquals("/images/b.jpg", images.get(1).getUrl());
        assertEquals(1, images.get(1).getSortOrder());
    }

    @Test
    @DisplayName("softDelete tra false khi khong co dong nao doi trang thai")
    void softDeleteReportsMissingRow() {
        when(productRepository.softDelete(anyLong(), any(LocalDateTime.class))).thenReturn(false);

        assertFalse(productDomainService.softDelete(404L));
    }

    /**
     * Chạy {@code create} rồi bắt lại chính đối tượng được đẩy xuống port.
     *
     * @param draft bản nháp đầu vào
     * @return sản phẩm sau khi domain service điền các trường server tự tính
     */
    private Product captureCreated(Product draft) {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productDomainService.create(draft, new Category().setId(101L), null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        return captor.getValue();
    }
}
