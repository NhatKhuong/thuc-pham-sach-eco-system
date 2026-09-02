package com.nss;

import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.infrastructure.persistence.mapper.BrandJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CategoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CouponJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.EmailConfirmationTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.IdempotencyKeyJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderItemJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderStatusHistoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OutboxEventJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.PasswordResetTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductImageJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.PurchaseRequestJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.RefreshTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ReviewJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserRoleJPAMapper;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chín endpoint mặt hàng công khai của backlog 0024 — §B.1 sản phẩm (6) + §B.2 danh mục (3).
 * <p>
 * Cùng khuôn với {@link HelloEndpointTest}/{@link SecurityRulesTest}: loại autoconfig JPA, mọi
 * {@code *JPAMapper} có bản giả. Trọng tâm ba việc mà chỉ MockMvc-trên-context-thật mới kiểm được
 * (không phải unit test thuần):
 * <ol>
 *   <li>ma trận ba ca của ADR 0007 cho {@code sort} — vắng mặt/hợp lệ/rác đi hết một vòng qua
 *       {@code GlobalExceptionHandler} thật;</li>
 *   <li>hai {@code @GetMapping("/products")} (có {@code params="ids"} và không) không đụng nhau;</li>
 *   <li>ba đường danh mục công khai không đòi token, đúng dòng {@code permitAll} mới thêm ở
 *       {@code SecurityConfig}.</li>
 * </ol>
 * Phần hành vi cần dữ liệu thật (lọc theo {@code effectivePrice}, xoá mềm, {@code q} khác admin) nằm
 * ở ma trận request thật trên server sống — mục Verification của ticket, không thuộc file này.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class PublicCatalogEndpointsTest {

    @MockBean
    private ProductJPAMapper productJPAMapper;

    @MockBean
    private ProductImageJPAMapper productImageJPAMapper;

    @MockBean
    private CategoryJPAMapper categoryJPAMapper;

    @MockBean
    private BrandJPAMapper brandJPAMapper;

    @MockBean
    private UserJPAMapper userJPAMapper;

    @MockBean
    private RefreshTokenJPAMapper refreshTokenJPAMapper;

    @MockBean
    private ReviewJPAMapper reviewJPAMapper;

    @MockBean
    private UserRoleJPAMapper userRoleJPAMapper;

    @MockBean
    private CouponJPAMapper couponJPAMapper;

    @MockBean
    private OrderJPAMapper orderJPAMapper;

    @MockBean
    private OrderItemJPAMapper orderItemJPAMapper;

    @MockBean
    private OrderStatusHistoryJPAMapper orderStatusHistoryJPAMapper;

    @MockBean
    private PasswordResetTokenJPAMapper passwordResetTokenJPAMapper;

    /** Backlog 0037 — xac nhan email: cung ly do voi passwordResetTokenJPAMapper o tren. */
    @MockBean
    private EmailConfirmationTokenJPAMapper emailConfirmationTokenJPAMapper;

    /** Backlog 0032 — Outbox + Kafka: outbox_event/idempotency_key co adapter tu ticket do tro di. */
    @MockBean
    private OutboxEventJPAMapper outboxEventJPAMapper;

    @MockBean
    private IdempotencyKeyJPAMapper idempotencyKeyJPAMapper;

    /** Backlog 0039 — purchase_request co adapter tu ticket do tro di (luong async). */
    @MockBean
    private PurchaseRequestJPAMapper purchaseRequestJPAMapper;

    private final MockMvc mockMvc;

    @Autowired
    PublicCatalogEndpointsTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    private void stubEmptyProductPage() {
        Page<Product> empty = new PageImpl<>(List.of());
        when(productJPAMapper.findPublicPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(empty);
    }

    // ========== §B.2 DANH MUC — CONG KHAI, KHONG CAN TOKEN ==========

    @Test
    @DisplayName("GET /api/categories cong khai, khong token van 200")
    void getCategoriesIsPublic() throws Exception {
        when(categoryJPAMapper.findAllByOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/categories?root=true goi dung nhanh danh muc goc")
    void getRootCategoriesUsesRootBranch() throws Exception {
        Category root = new Category().setId(1L).setSlug("rau-cu").setName("Rau củ");
        when(categoryJPAMapper.findByParentIsNullOrderByNameAsc()).thenReturn(List.of(root));

        mockMvc.perform(get("/api/categories").param("root", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("rau-cu"))
                .andExpect(jsonPath("$[0].parentId").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/categories/{slug}: khong ton tai -> 404, KHONG phai 401")
    void getCategoryBySlugNotFoundReturns404() throws Exception {
        when(categoryJPAMapper.findBySlug(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/categories/khong-ton-tai"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/categories/{slug}: parentId cua danh muc con phan anh dung id cha")
    void getCategoryBySlugMapsParentId() throws Exception {
        Category parent = new Category().setId(1L).setSlug("rau-cu").setName("Rau củ");
        Category child = new Category().setId(2L).setSlug("rau-an-la").setName("Rau ăn lá").setParent(parent);
        when(categoryJPAMapper.findBySlug("rau-an-la")).thenReturn(Optional.of(child));

        mockMvc.perform(get("/api/categories/rau-an-la"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("rau-an-la"))
                .andExpect(jsonPath("$.parentId").value(1));
    }

    // ========== §B.1 GET /products — ADR 0007 BA CA CHO sort ==========

    @ParameterizedTest(name = "sort={0} -> 200")
    @ValueSource(strings = {"newest", "price_asc", "price_desc", "best_selling", "rating"})
    @DisplayName("sort hop le -> 200")
    void validSortReturns200(String sort) throws Exception {
        stubEmptyProductPage();

        mockMvc.perform(get("/api/products").param("sort", sort))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sort VANG MAT -> 200, KHONG loc (control duong: rong khac rac)")
    void absentSortReturns200() throws Exception {
        stubEmptyProductPage();

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("sort=gia-tri-rac -> 422 dung §A.3, errors co khoa 'sort'")
    void garbageSortReturns422WithFieldError() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "gia-tri-rac"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors.sort").exists())
                .andExpect(jsonPath("$.errors.sort").value(Matchers.containsString("newest")));
    }

    @Test
    @DisplayName("category=slug-bia -> 200, items rong, total 0 (ADR 0007 ve 2 — tap rong, khong phai loi)")
    void garbageCategoryReturnsEmptySetNot422() throws Exception {
        stubEmptyProductPage();

        mockMvc.perform(get("/api/products").param("category", "slug-bia-khong-ton-tai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ========== §B.1 GET /products?ids= khong dung voi GET /products thuong ==========

    @Test
    @DisplayName("GET /products?ids= di dung nhanh rieng, tra mang phang KHONG phan trang")
    void getProductsWithIdsRoutesToSeparateHandler() throws Exception {
        Product product = new Product().setId(1L).setSlug("ca-rot").setName("Cà rốt")
                .setPrice(20000L).setUnit("kg").setStock(10).setSold(0)
                .setRating(new BigDecimal("0.0")).setReviewCount(0)
                .setIsFeatured(false).setIsBestSeller(false)
                .setCreatedAt(LocalDateTime.now());
        when(productJPAMapper.findActiveByIdIn(any())).thenReturn(List.of(product));
        when(productImageJPAMapper.findByProductIdIn(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/products").param("ids", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                // Khong phai PaginatedResponse: khong co truong "items"/"total" o goc mang
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    // ========== §B.1 GET /products/{slug}/related ==========

    @Test
    @DisplayName("GET /products/{slug}/related: slug goc khong ton tai -> 404")
    void relatedProductsBaseSlugNotFoundReturns404() throws Exception {
        when(productJPAMapper.findActiveBySlug(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/khong-ton-tai/related"))
                .andExpect(status().isNotFound());
    }

    // ========== §B.1 GET /products/price-range ==========

    @Test
    @DisplayName("GET /products/price-range tra dung hinh dang { min, max }")
    void priceRangeReturnsMinMaxShape() throws Exception {
        when(productJPAMapper.findMinEffectivePrice()).thenReturn(15000L);
        when(productJPAMapper.findMaxEffectivePrice()).thenReturn(890000L);

        mockMvc.perform(get("/api/products/price-range"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.min").value(15000))
                .andExpect(jsonPath("$.max").value(890000));
    }

    // ========== §B.1 GET /products/suggest ==========

    @Test
    @DisplayName("GET /products/suggest voi q rong -> 200, mang rong")
    void suggestWithBlankQueryReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/products/suggest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ========== §A.4 PHAN TRANG: vuot trang cuoi ==========

    @Test
    @DisplayName("page vuot so trang -> items rong, KHONG phai 404")
    void pageBeyondLastReturnsEmptyItemsNot404() throws Exception {
        Page<Product> pageOne = new PageImpl<>(List.of(), Pageable.ofSize(12), 5);
        when(productJPAMapper.findPublicPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pageOne);

        mockMvc.perform(get("/api/products").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalPages").value(1));
    }
}
