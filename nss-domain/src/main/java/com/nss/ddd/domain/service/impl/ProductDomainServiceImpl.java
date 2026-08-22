package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.PageResult;
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
import java.text.Normalizer;
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

    /** Dấu thanh và dấu phụ sau khi tách bằng NFD — bảng Unicode "Combining Diacritical Marks". */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

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

    @Override
    public Product findBySlug(String slug) {
        return productRepository.findBySlug(slug).orElse(null);
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
     * Hai bước, và bước thứ hai là bước hay bị quên: {@link Normalizer} tách được dấu thanh khỏi
     * nguyên âm, nhưng <b>{@code đ} không phải nguyên âm có dấu</b> — nó là một ký tự Latin riêng,
     * NFD không tách nó ra được. Thiếu bước đó thì "đậu" ra "đau" chứ không phải "dau", và tìm kiếm
     * bỏ dấu trượt đúng những từ tiếng Việt hay gặp nhất.
     *
     * @param name tên hiển thị
     * @return tên đã bỏ dấu và hạ chữ thường, hoặc {@code null} khi {@code name} rỗng
     */
    private String genNameNormalized(String name) {
        if (name == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return withoutMarks
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase();
    }
}
