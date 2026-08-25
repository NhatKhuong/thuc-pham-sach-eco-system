package com.nss.ddd.application.service.product.impl;

import com.nss.ddd.application.mapper.ProductMapper;
import com.nss.ddd.application.model.command.CreateProductCommand;
import com.nss.ddd.application.model.command.UpdateProductCommand;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.model.response.ProductMutationResponse;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.application.service.product.ProductAppService;
import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.entity.Brand;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ProductImage;
import com.nss.ddd.domain.service.ProductDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Hiện thực use case CRUD sản phẩm.
 * <p>
 * Tầng này chỉ điều phối: hỏi domain service, rồi lắp kết quả thành kiểu của bề mặt dây.
 * Không có quy tắc nghiệp vụ nào nằm ở đây.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAppServiceImpl implements ProductAppService {

    /** Mặc định của API_CONTRACT §A.4 cho danh sách sản phẩm. */
    private static final int DEFAULT_LIMIT = 12;

    private static final String MESSAGE_PRODUCT_NOT_FOUND = "Không tìm thấy sản phẩm này.";

    private static final String MESSAGE_INVALID_SALE_PRICE = "Giá khuyến mãi phải nhỏ hơn giá gốc.";

    private static final String MESSAGE_CATEGORY_NOT_FOUND = "Danh mục được chọn không tồn tại.";

    private static final String MESSAGE_BRAND_NOT_FOUND = "Thương hiệu được chọn không tồn tại.";

    /**
     * Không sinh được slug — tên và slug đều không còn ký tự hợp lệ nào sau khi bỏ dấu (ví dụ tên
     * chỉ gồm ký hiệu). Frontend ném lỗi ở đúng ca này ({@code adminProducts.api.ts:118}).
     */
    private static final String MESSAGE_SLUG_NOT_GENERATED =
            "Không sinh được slug từ tên sản phẩm, vui lòng nhập slug thủ công.";

    private final ProductDomainService productDomainService;

    // ========== READ ==========

    @Override
    public PaginatedResponse<ProductResponse> findProducts(int page, int limit) {
        // 1. Keo tham so ve khoang dung duoc; `page` van danh so tu 1 tren duong day
        int safePage = Math.max(page, 1);
        int safeLimit = limit < 1 ? DEFAULT_LIMIT : limit;
        // 2. Domain tra ve entity + tong so dong; phep tru 1 nam trong adapter
        PageResult<Product> pageResult = productDomainService.findPage(safePage, safeLimit);
        log.info("findProducts: success | page={} limit={} total={}", safePage, safeLimit, pageResult.getTotal());
        return toPaginatedResponse(pageResult, safePage, safeLimit);
    }

    @Override
    public PaginatedResponse<ProductResponse> findAdminProducts(ProductFilter filter) {
        // 1. Keo tham so ve khoang dung duoc — dung mot luat voi trang cua hang (§A.4)
        int safePage = Math.max(filter.getPage(), 1);
        int safeLimit = filter.getLimit() < 1 ? DEFAULT_LIMIT : filter.getLimit();
        // 2. Dung filter MOI thay vi sua cai duoc truyen vao: doi tuong cua phia goi khong duoc
        //    am tham doi nghia giua chung mot lan xu ly
        ProductFilter safeFilter = ProductFilter.of(filter.getKeyword(), filter.getCategorySlug(),
                filter.getStockStatus(), filter.getSort(), safePage, safeLimit);
        PageResult<Product> pageResult = productDomainService.findAdminPage(safeFilter);
        log.info("findAdminProducts: success | q={} category={} stockStatus={} sort={} page={} limit={} total={}",
                safeFilter.getKeyword(), safeFilter.getCategorySlug(), safeFilter.getStockStatus(),
                safeFilter.getSort(), safePage, safeLimit, pageResult.getTotal());
        return toPaginatedResponse(pageResult, safePage, safeLimit);
    }

    @Override
    public ProductResponse findProductBySlug(String slug) {
        Product product = productDomainService.findBySlug(slug);
        if (product == null) {
            log.warn("findProductBySlug: not found | slug={}", slug);
            return null;
        }
        log.info("findProductBySlug: success | productId={} slug={}", product.getId(), slug);
        return ProductMapper.toResponse(product, productDomainService.findImages(product.getId()));
    }

    @Override
    public ProductResponse findProductById(Long id) {
        Product product = productDomainService.findById(id);
        if (product == null) {
            log.warn("findProductById: not found | productId={}", id);
            return null;
        }
        log.info("findProductById: success | productId={}", id);
        return ProductMapper.toResponse(product, productDomainService.findImages(id));
    }

    // ========== WRITE ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductMutationResponse createProduct(CreateProductCommand command) {
        // 1. Chot slug TRUOC moi buoc khac. Slug client gui cung duoc slugify (§B.12.1), nen
        //    "Ca-Rot" va "ca-rot" la CUNG mot slug; kiem trung tren chuoi tho se cho hai san pham
        //    di qua cong kiem roi cung chet o rang buoc uk_slug — mot loi 500 thay cho mot 409.
        //    Ghi nguoc vao command de ba cho doc slug ve sau (kiem trung, so voi slug cu khi sua,
        //    ban ghi xuong DB) deu thay dung mot chuoi.
        String slug = productDomainService.genSlug(command.getSlug(), command.getName());
        if (slug == null) {
            log.warn("createProduct: slug not generated | name={}", command.getName());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_INVALID_PRODUCT_DATA,
                    MESSAGE_SLUG_NOT_GENERATED);
        }
        command.setSlug(slug);
        // 2. Slug la khoa duy nhat tren toan bang — kiem truoc de tra 409 thay vi loi rang buoc
        if (productDomainService.hasSlugTaken(command.getSlug())) {
            log.warn("createProduct: duplicate slug | slug={}", command.getSlug());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_DUPLICATE_SLUG,
                    genDuplicateSlugMessage(command.getSlug()));
        }
        // 3. Quy tac gia
        if (!productDomainService.hasValidSalePrice(command.getPrice(), command.getSalePrice())) {
            log.warn("createProduct: invalid sale price | price={} salePrice={}",
                    command.getPrice(), command.getSalePrice());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_INVALID_PRODUCT_DATA,
                    MESSAGE_INVALID_SALE_PRICE);
        }
        // 4. Phan giai quan he
        Category category = productDomainService.findCategoryById(command.getCategoryId());
        if (category == null) {
            log.warn("createProduct: category not found | categoryId={}", command.getCategoryId());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_INVALID_PRODUCT_DATA,
                    MESSAGE_CATEGORY_NOT_FOUND);
        }
        Brand brand = productDomainService.findBrandById(command.getBrandId());
        if (command.getBrandId() != null && brand == null) {
            log.warn("createProduct: brand not found | brandId={}", command.getBrandId());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_INVALID_PRODUCT_DATA,
                    MESSAGE_BRAND_NOT_FOUND);
        }
        // 5. Ghi san pham va anh con trong CUNG transaction cua method nay
        Product saved = productDomainService.create(ProductMapper.toEntity(command), category, brand);
        List<ProductImage> images = productDomainService.replaceImages(saved, command.getImages());
        log.info("createProduct: success | productId={} slug={}", saved.getId(), saved.getSlug());
        return ProductMutationResponse.success(ProductMapper.toResponse(saved, images));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductMutationResponse updateProduct(Long id, UpdateProductCommand command) {
        // 1. San pham da xoa mem hanh xu nhu the no khong ton tai
        Product existing = productDomainService.findById(id);
        if (existing == null) {
            log.warn("updateProduct: not found | productId={}", id);
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_PRODUCT_NOT_FOUND,
                    MESSAGE_PRODUCT_NOT_FOUND);
        }
        // 2. Chot slug TRUOC khi kiem trung — cung ly do nhu o createProduct
        String slug = productDomainService.genSlug(command.getSlug(), command.getName());
        if (slug == null) {
            log.warn("updateProduct: slug not generated | productId={} name={}", id, command.getName());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_INVALID_PRODUCT_DATA,
                    MESSAGE_SLUG_NOT_GENERATED);
        }
        command.setSlug(slug);
        // 3. Chi kiem trung slug KHI slug that su doi — khong thi san pham se dung do chinh no.
        //    Phep so sanh nay chay tren slug DA chuan hoa, nen sua san pham ma giu nguyen slug cu
        //    (hoac go lai chinh no voi chu hoa) van la 200, khong phai 409 (§B.12.1 dieu 9).
        if (!existing.getSlug().equals(command.getSlug())
                && productDomainService.hasSlugTaken(command.getSlug())) {
            log.warn("updateProduct: duplicate slug | productId={} slug={}", id, command.getSlug());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_DUPLICATE_SLUG,
                    genDuplicateSlugMessage(command.getSlug()));
        }
        // 4. Quy tac gia
        if (!productDomainService.hasValidSalePrice(command.getPrice(), command.getSalePrice())) {
            log.warn("updateProduct: invalid sale price | productId={} price={} salePrice={}",
                    id, command.getPrice(), command.getSalePrice());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_INVALID_PRODUCT_DATA,
                    MESSAGE_INVALID_SALE_PRICE);
        }
        // 5. Phan giai quan he
        Category category = productDomainService.findCategoryById(command.getCategoryId());
        if (category == null) {
            log.warn("updateProduct: category not found | productId={} categoryId={}", id, command.getCategoryId());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_INVALID_PRODUCT_DATA,
                    MESSAGE_CATEGORY_NOT_FOUND);
        }
        Brand brand = productDomainService.findBrandById(command.getBrandId());
        if (command.getBrandId() != null && brand == null) {
            log.warn("updateProduct: brand not found | productId={} brandId={}", id, command.getBrandId());
            return ProductMutationResponse.failed(ProductMutationResponse.CODE_INVALID_PRODUCT_DATA,
                    MESSAGE_BRAND_NOT_FOUND);
        }
        // 6. Ghi san pham va thay tron mang anh trong CUNG transaction
        Product saved = productDomainService.update(ProductMapper.applyUpdate(existing, command), category, brand);
        List<ProductImage> images = productDomainService.replaceImages(saved, command.getImages());
        log.info("updateProduct: success | productId={} slug={}", saved.getId(), saved.getSlug());
        return ProductMutationResponse.success(ProductMapper.toResponse(saved, images));
    }

    @Override
    @Transactional
    public boolean deleteProduct(Long id) {
        boolean deleted = productDomainService.softDelete(id);
        if (deleted) {
            log.info("deleteProduct: success | productId={}", id);
        } else {
            log.warn("deleteProduct: not found | productId={}", id);
        }
        return deleted;
    }

    // ========== HELPERS ==========

    /**
     * Lắp một trang entity thành {@code Paginated<Product>} của bề mặt dây.
     * <p>
     * <b>Dùng chung cho cả trang cửa hàng lẫn bảng quản trị, và điều đó là cố ý:</b> bước gom ảnh
     * theo lô ở dưới là thứ giữ đường đọc danh sách khỏi N+1. Chép nó ra hai bản thì bản thứ hai sẽ
     * là bản bị quên — và triệu chứng không phải một lỗi mà là một trang chậm dần theo số sản phẩm,
     * thứ chỉ lộ ra khi dữ liệu đủ lớn.
     *
     * @param pageResult trang entity kèm tổng số dòng khớp điều kiện
     * @param page trang hiện tại, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return trang đã dựng xong theo §A.4
     */
    private PaginatedResponse<ProductResponse> toPaginatedResponse(PageResult<Product> pageResult,
                                                                   int page, int limit) {
        List<Product> products = pageResult.getItems();
        // Anh cua ca trang lay trong MOT truy van, tranh N+1
        Map<Long, List<ProductImage>> imagesByProductId = productDomainService
                .findImagesGroupedByProductId(products.stream().map(Product::getId).toList());
        List<ProductResponse> items = new ArrayList<>(products.size());
        for (Product product : products) {
            items.add(ProductMapper.toResponse(product,
                    imagesByProductId.getOrDefault(product.getId(), Collections.emptyList())));
        }
        return PaginatedResponse.of(items, pageResult.getTotal(), page, limit);
    }

    /**
     * Dựng thông điệp tiếng Việt cho lỗi trùng slug — chuỗi này đi thẳng vào {@code detail} của
     * {@code ProblemDetail} và được frontend hiển thị nguyên văn cho người dùng cuối (§A.3).
     *
     * @param slug slug bị trùng
     * @return thông điệp tiếng Việt
     */
    private String genDuplicateSlugMessage(String slug) {
        return "Slug \"" + slug + "\" đã được dùng cho một sản phẩm khác, vui lòng chọn slug khác.";
    }
}
