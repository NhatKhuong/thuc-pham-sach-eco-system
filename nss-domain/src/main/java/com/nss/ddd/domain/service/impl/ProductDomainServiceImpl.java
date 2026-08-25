package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.StockStatus;
import com.nss.ddd.domain.model.TextNormalizer;
import com.nss.ddd.domain.model.entity.Brand;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ProductImage;
import com.nss.ddd.domain.repository.BrandRepository;
import com.nss.ddd.domain.repository.CategoryRepository;
import com.nss.ddd.domain.repository.ProductImageRepository;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.service.ProductDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hiện thực domain service của {@code Product}.
 * <p>
 * Phụ thuộc duy nhất là bốn port — không có tham chiếu nào tới module infrastructure ở compile-time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductDomainServiceImpl implements ProductDomainService {

    /**
     * Mọi ký tự <b>không</b> được phép có mặt trong slug: giữ lại {@code a-z}, {@code 0-9}, khoảng
     * trắng và gạch ngang.
     * <p>
     * {@code UNICODE_CHARACTER_CLASS} để {@code \s} bắt cả khoảng trắng Unicode (nhất là
     * {@code U+00A0} no-break space, thứ hay lọt vào khi copy tên sản phẩm từ trình duyệt). Mặc
     * định của Java, {@code \s} chỉ là bảy ký tự ASCII, còn {@code \s} của JavaScript thì theo
     * Unicode — thiếu cờ này thì {@code "Cà rốt"} ra {@code carot} ở backend và
     * {@code ca-rot} ở frontend, hai slug khác nhau cho cùng một cái tên.
     */
    private static final Pattern SLUG_FORBIDDEN =
            Pattern.compile("[^a-z0-9\\s-]", Pattern.UNICODE_CHARACTER_CLASS);

    /** Một hoặc nhiều khoảng trắng liên tiếp — cùng lý do dùng {@code UNICODE_CHARACTER_CLASS}. */
    private static final Pattern SLUG_WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /** Hai gạch ngang trở lên liên tiếp, gộp lại thành một. */
    private static final Pattern SLUG_DASHES = Pattern.compile("-+");

    /** Điểm đánh giá của sản phẩm chưa có lượt nào — {@code DECIMAL(2,1)} nên phải đúng 1 chữ số thập phân. */
    private static final BigDecimal RATING_NONE = new BigDecimal("0.0");

    private final ProductRepository productRepository;

    private final ProductImageRepository productImageRepository;

    private final CategoryRepository categoryRepository;

    private final BrandRepository brandRepository;

    // ========== READ ==========

    @Override
    public PageResult<Product> findPage(int page, int limit) {
        return productRepository.findPage(page, limit);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Dựng một {@code ProductFilter} MỚI thay vì sửa cái được truyền vào.</b> Sửa tại chỗ thì
     * đối tượng của phía gọi âm thầm đổi nghĩa giữa chừng — {@code keyword} vào là chuỗi có dấu, ra
     * là chuỗi không dấu — và một dòng log ở tầng trên in ra sau lời gọi này sẽ nói sai về chính
     * cái request nó đang xử lý.
     */
    @Override
    public PageResult<Product> findAdminPage(ProductFilter filter) {
        return productRepository.findAdminPage(ProductFilter.of(
                genSearchKeyword(filter.getKeyword()),
                filter.getCategorySlug(),
                filter.getStockStatus(),
                filter.getSort(),
                filter.getPage(),
                filter.getLimit()));
    }

    @Override
    public Product findBySlug(String slug) {
        return productRepository.findBySlug(slug).orElse(null);
    }

    @Override
    public long countLowStockProducts() {
        // Bo loc dung y het GET /admin/products?stockStatus=low_stock: `sort` / `page` / `limit`
        // khong tham gia phep dem, nen truyen gia tri trung tinh.
        return productRepository.countAdminProducts(
                ProductFilter.of(null, null, StockStatus.LOW_STOCK, null, 1, 1));
    }

    @Override
    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    @Override
    public Category findCategoryById(Long id) {
        if (id == null) {
            return null;
        }
        return categoryRepository.findById(id).orElse(null);
    }

    @Override
    public Brand findBrandById(Long id) {
        if (id == null) {
            return null;
        }
        return brandRepository.findById(id).orElse(null);
    }

    // ========== BUSINESS RULES ==========

    @Override
    public boolean hasSlugTaken(String slug) {
        return productRepository.existsBySlug(slug);
    }

    @Override
    public String genSlug(String requestedSlug, String name) {
        // Slug client gui CUNG duoc slugify, khong chi khi bo trong (adminProducts.api.ts:117)
        String source = requestedSlug == null || requestedSlug.isBlank() ? name : requestedSlug;
        return genSlugified(source);
    }

    @Override
    public boolean hasValidSalePrice(Long price, Long salePrice) {
        if (salePrice == null) {
            return true;
        }
        if (price == null) {
            return false;
        }
        return salePrice < price;
    }

    // ========== WRITE ==========

    @Override
    public Product create(Product draft, Category category, Brand brand) {
        // 1. Thời điểm: LocalDateTime.now(ZoneOffset.UTC), KHONG phai now() — now() lay gio may,
        //    lech 7 tieng o VN va khong co gi bao loi.
        LocalDateTime now = genUtcNow();
        // 2. Trường server tự tính — client gửi lên cũng bị ghi đè ở đây
        draft.setNameNormalized(genNameNormalized(draft.getName()))
                .setCategory(category)
                .setBrand(brand)
                .setIsActive(Boolean.TRUE)
                .setRating(RATING_NONE)
                .setReviewCount(0)
                .setSold(0)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        // 3. Cờ hiển thị là tuỳ chọn của client; thiếu thì mặc định tắt
        draft.setIsFeatured(Boolean.TRUE.equals(draft.getIsFeatured()))
                .setIsBestSeller(Boolean.TRUE.equals(draft.getIsBestSeller()));
        Product saved = productRepository.save(draft);
        log.info("create: saved product | productId={} slug={}", saved.getId(), saved.getSlug());
        return saved;
    }

    @Override
    public Product update(Product product, Category category, Brand brand) {
        product.setNameNormalized(genNameNormalized(product.getName()))
                .setCategory(category)
                .setBrand(brand)
                .setUpdatedAt(genUtcNow());
        Product saved = productRepository.save(product);
        log.info("update: saved product | productId={} slug={}", saved.getId(), saved.getSlug());
        return saved;
    }

    @Override
    public boolean softDelete(Long id) {
        boolean deleted = productRepository.softDelete(id, genUtcNow());
        if (!deleted) {
            log.warn("softDelete: no active row matched | productId={}", id);
        }
        return deleted;
    }

    // ========== IMAGES ==========

    @Override
    public List<ProductImage> findImages(Long productId) {
        return productImageRepository.findByProductId(productId);
    }

    @Override
    public Map<Long, List<ProductImage>> findImagesGroupedByProductId(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productImageRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(image -> image.getProduct().getId()));
    }

    @Override
    public List<ProductImage> replaceImages(Product product, List<String> urls) {
        // 1. Mảng ảnh mới thay TRỌN mảng cũ — xoá trước, chèn sau, cùng một transaction của tầng trên
        productImageRepository.deleteByProductId(product.getId());
        if (urls == null || urls.isEmpty()) {
            return Collections.emptyList();
        }
        // 2. sortOrder chính là vị trí trong danh sách client gửi lên
        List<ProductImage> images = new ArrayList<>(urls.size());
        for (int index = 0; index < urls.size(); index++) {
            images.add(new ProductImage()
                    .setProduct(product)
                    .setUrl(urls.get(index))
                    .setSortOrder(index));
        }
        return productImageRepository.saveAll(images);
    }

    // ========== HELPERS ==========

    /**
     * Mốc thời gian chuẩn của aggregate này.
     * <p>
     * Hai quyết định, cả hai đều sửa một lỗi không tự báo:
     * <ul>
     *   <li><b>{@code now(ZoneOffset.UTC)}, không phải {@code now()}.</b> {@code now()} lấy đồng hồ
     *       máy; trên máy dev ở UTC+7 nó ghi xuống DB một mốc lệch 7 tiếng so với dữ liệu seed, và
     *       không có gì báo lỗi (0004 §Contract điểm 3).</li>
     *   <li><b>Cắt về micro-giây.</b> Cột là {@code datetime(6)} nên MySQL chỉ giữ 6 chữ số; để
     *       nguyên nano thì entity trong bộ nhớ và dòng trong DB lệch nhau ở phần lẻ, và cùng một
     *       sản phẩm trả ra hai chuỗi {@code createdAt} khác nhau tuỳ theo nó vừa được POST hay
     *       vừa được đọc lại.</li>
     * </ul>
     *
     * @return giờ UTC hiện tại, đã cắt về đúng độ chính xác của cột
     */
    private LocalDateTime genUtcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Sinh {@code name_normalized}: bỏ dấu, hạ chữ thường.
     * <p>
     * <b>Uỷ thác cho {@link TextNormalizer#genNormalized(String)} — phép bỏ dấu có đúng MỘT bản
     * trong toàn dự án</b> ({@code coding-conventions.md} §18). Method này giữ lại vì nó là tên mà
     * §18 gọi đích danh, và vì nó nói đúng ngữ cảnh: <i>đây</i> là nơi giá trị của cột
     * {@code name_normalized} được sinh ra. Từ backlog 0019 có thêm hai cột nữa dùng chung phép
     * này ({@code customer_order.full_name_normalized}, {@code user.full_name_normalized}), nên
     * phần cài đặt đã chuyển lên chỗ dùng chung thay vì bị chép thành bản thứ hai.
     *
     * @param name tên hiển thị
     * @return tên đã bỏ dấu và hạ chữ thường, hoặc {@code null} khi {@code name} rỗng
     */
    private String genNameNormalized(String name) {
        return TextNormalizer.genNormalized(name);
    }

    /**
     * Chuẩn hoá từ khoá {@code q} về đúng dạng đang nằm trong cột {@code name_normalized}.
     * <p>
     * <b>Uỷ thác cho {@link TextNormalizer#genSearchKeyword(String)}</b> — cùng lý do đã viết ở
     * {@link #genNameNormalized(String)}: hai vế của một phép so sánh chuỗi phải đi qua cùng một
     * hàm, và hàm đó chỉ được có một bản.
     * <p>
     * <b>Khác biệt đã biết với frontend, và nó nghiêng về phía an toàn.</b> Hàm {@code normalize()}
     * của {@code adminProducts.api.ts:45-47} <i>không</i> đổi {@code đ} thành {@code d} — chỉ
     * {@code slugify} mới làm. Backend thì có, và còn khớp thêm cả cột {@code slug}, nên tập kết
     * quả của backend là <b>siêu tập</b> của mock: {@code q=dau} ra "Đậu Hà Lan" qua cả tên lẫn
     * slug, mock chỉ ra qua slug. Ghi ở {@code coding-conventions.md} §18.
     *
     * @param keyword từ khoá thô client gửi, có thể {@code null}
     * @return từ khoá đã bỏ dấu và hạ chữ thường, hoặc {@code null} khi không có gì để tìm
     */
    private String genSearchKeyword(String keyword) {
        return TextNormalizer.genSearchKeyword(keyword);
    }

    /**
     * Bảy bước sinh slug, theo đúng thứ tự của {@code slugify} ở {@code src/lib/utils.ts:21-32}.
     * <p>
     * Bốn bước đầu chính là {@link #genNameNormalized(String)}; ba bước sau là phần riêng của slug.
     * <b>Thứ tự {@code trim} trước khi đổi khoảng trắng thành gạch ngang là load-bearing:</b> đảo
     * lại thì {@code " Cà rốt "} ra {@code -ca-rot-} thay vì {@code ca-rot}, và cái slug ấy đi
     * thẳng lên URL công khai.
     *
     * @param text chuỗi nguồn
     * @return slug, hoặc {@code null} khi nguồn rỗng hoặc không còn ký tự hợp lệ nào
     */
    private String genSlugified(String text) {
        if (text == null) {
            return null;
        }
        // 1-4. NFD, bo dau phu, d/D, ha chu thuong — dung lai dung ham sinh name_normalized
        String normalized = genNameNormalized(text);
        // 5. trim TRUOC khi doi khoang trang thanh gach ngang
        String trimmed = normalized.trim();
        // 6. bo moi ky tu ngoai [a-z0-9], khoang trang, gach ngang
        String allowedOnly = SLUG_FORBIDDEN.matcher(trimmed).replaceAll("");
        // 7. khoang trang lien tiep -> mot gach ngang, roi gop gach ngang lien tiep
        String hyphenated = SLUG_WHITESPACE.matcher(allowedOnly).replaceAll("-");
        String slug = SLUG_DASHES.matcher(hyphenated).replaceAll("-");
        if (slug.isEmpty()) {
            log.warn("genSlugified: empty slug from source text");
            return null;
        }
        return slug;
    }
}
