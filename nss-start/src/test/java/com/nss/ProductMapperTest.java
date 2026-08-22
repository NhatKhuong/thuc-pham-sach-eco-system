package com.nss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.mapper.ProductMapper;
import com.nss.ddd.application.model.command.CreateProductCommand;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.domain.model.entity.Brand;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ProductImage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm {@code ProductMapper} — logic thuần, không cần Spring context và không cần database.
 * <p>
 * Phép kiểm đáng giá nhất ở đây là {@link #responseJsonNeverLeaksIsActive()}: nó tuần tự hoá thật
 * bằng Jackson thay vì đọc danh sách field bằng mắt, nên nó bắt được cả trường hợp ai đó thêm
 * {@code isActive} vào {@code ProductResponse} về sau.
 */
class ProductMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("toResponse map du field cua type Product phia client")
    void toResponseMapsEveryClientField() {
        ProductResponse response = ProductMapper.toResponse(genProduct(), genImages());

        assertEquals(7L, response.getId());
        assertEquals("ca-rot-huu-co", response.getSlug());
        assertEquals("Cà rốt hữu cơ", response.getName());
        assertEquals(45000L, response.getPrice());
        assertEquals(39000L, response.getSalePrice());
        assertEquals(101L, response.getCategoryId());
        assertEquals(3L, response.getBrandId());
        assertEquals(new BigDecimal("4.5"), response.getRating());
        assertEquals(2, response.getReviewCount());
        assertEquals(120, response.getStock());
        assertEquals(380, response.getSold());
        assertEquals("kg", response.getUnit());
        assertEquals("Đà Lạt", response.getOrigin());
        assertEquals(Boolean.TRUE, response.getIsFeatured());
        assertEquals(Boolean.FALSE, response.getIsBestSeller());
    }

    @Test
    @DisplayName("images giu dung thu tu sortOrder ma repository tra ve")
    void toResponseKeepsImageOrder() {
        ProductResponse response = ProductMapper.toResponse(genProduct(), genImages());

        assertEquals(List.of("/images/rau-cu/ca-rot-1.jpg", "/images/rau-cu/ca-rot-2.jpg"),
                response.getImages());
    }

    @Test
    @DisplayName("images rong tra mang rong chu khong phai null")
    void toResponseReturnsEmptyListWhenNoImage() {
        ProductResponse response = ProductMapper.toResponse(genProduct(), null);

        assertEquals(List.of(), response.getImages());
    }

    @Test
    @DisplayName("createdAt la chuoi ISO 8601 co hau to Z")
    void toResponseFormatsCreatedAtAsIsoUtc() {
        ProductResponse response = ProductMapper.toResponse(genProduct(), genImages());

        assertEquals("2026-07-02T08:30:00Z", response.getCreatedAt());
    }

    @Test
    @DisplayName("brand rong khong lam vo mapper")
    void toResponseHandlesNullBrand() {
        Product product = genProduct().setBrand(null);

        assertNull(ProductMapper.toResponse(product, genImages()).getBrandId());
    }

    @Test
    @DisplayName("mapper null-guard ca hai chieu")
    void mapperIsNullGuarded() {
        assertNull(ProductMapper.toResponse(null, genImages()));
        assertNull(ProductMapper.toEntity(null));
        assertNull(ProductMapper.applyUpdate(null, null));
    }

    @Test
    @DisplayName("JSON cua ProductResponse KHONG bao gio chua isActive")
    void responseJsonNeverLeaksIsActive() throws Exception {
        String json = objectMapper.writeValueAsString(ProductMapper.toResponse(genProduct(), genImages()));

        // Chung minh phep kiem nay biet can: mot khoa CHAC CHAN co mat phai tim thay duoc
        assertTrue(json.contains("\"slug\""), "Body khong co khoa slug — phep grep dang chay sai cho");
        assertFalse(json.contains("isActive"), "isActive ro ra response — day la thay doi contract");
        assertFalse(json.contains("is_active"), "is_active ro ra response — day la thay doi contract");
        assertFalse(json.contains("nameNormalized"), "nameNormalized ro ra response");
        assertFalse(json.contains("effectivePrice"), "effectivePrice ro ra response");
    }

    @Test
    @DisplayName("toEntity khong dung toi truong server tu tinh")
    void toEntityLeavesServerOwnedFieldsUntouched() {
        CreateProductCommand command = new CreateProductCommand()
                .setSlug("ca-rot-huu-co")
                .setName("Cà rốt hữu cơ")
                .setPrice(45000L)
                .setStock(10);

        Product entity = ProductMapper.toEntity(command);

        assertEquals("ca-rot-huu-co", entity.getSlug());
        assertNull(entity.getNameNormalized(), "nameNormalized la viec cua domain service");
        assertNull(entity.getIsActive(), "isActive la viec cua domain service");
        assertNull(entity.getCreatedAt(), "createdAt la viec cua domain service");
        assertNull(entity.getRating(), "rating khong nhan tu client");
        assertNull(entity.getSold(), "sold khong nhan tu client");
    }

    /**
     * @return một sản phẩm đầy đủ trường, {@code createdAt} là một mốc cố định để khẳng định được
     *         chuỗi ISO sinh ra
     */
    private Product genProduct() {
        return new Product()
                .setId(7L)
                .setSlug("ca-rot-huu-co")
                .setName("Cà rốt hữu cơ")
                .setNameNormalized("ca rot huu co")
                .setShortDescription("Cà rốt Đà Lạt")
                .setDescription("Mô tả đầy đủ")
                .setPrice(45000L)
                .setSalePrice(39000L)
                .setEffectivePrice(39000L)
                .setUnit("kg")
                .setOrigin("Đà Lạt")
                .setStock(120)
                .setSold(380)
                .setRating(new BigDecimal("4.5"))
                .setReviewCount(2)
                .setIsFeatured(Boolean.TRUE)
                .setIsBestSeller(Boolean.FALSE)
                .setIsActive(Boolean.TRUE)
                .setCategory(new Category().setId(101L))
                .setBrand(new Brand().setId(3L))
                .setCreatedAt(LocalDateTime.of(2026, 7, 2, 8, 30, 0));
    }

    /**
     * @return hai ảnh theo đúng thứ tự repository trả về
     */
    private List<ProductImage> genImages() {
        return List.of(
                new ProductImage().setId(1L).setUrl("/images/rau-cu/ca-rot-1.jpg").setSortOrder(0),
                new ProductImage().setId(2L).setUrl("/images/rau-cu/ca-rot-2.jpg").setSortOrder(1));
    }
}
