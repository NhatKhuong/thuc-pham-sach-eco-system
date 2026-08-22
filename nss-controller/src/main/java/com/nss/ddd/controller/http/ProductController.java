package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.model.response.ProductMutationResponse;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.application.service.product.ProductAppService;
import com.nss.ddd.controller.dto.CreateProductRequest;
import com.nss.ddd.controller.dto.UpdateProductRequest;
import com.nss.ddd.controller.exception.DuplicateSlugException;
import com.nss.ddd.controller.exception.InvalidProductDataException;
import com.nss.ddd.controller.exception.ProductNotFoundException;
import com.nss.ddd.controller.mapper.ProductControllerMapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biên REST của sản phẩm — API_CONTRACT §B.1.
 * <p>
 * Trả DTO trần, <b>không bọc {@code ResultMessage}</b> (ADR 0001, giống {@code HelloController});
 * thất bại dùng mã HTTP thật và {@code ProblemDetail} do {@code GlobalExceptionHandler} dựng.
 * <p>
 * <b>Đọc bằng {@code slug}, ghi bằng {@code id}.</b> Đọc theo slug vì frontend dựng URL từ slug;
 * ghi theo id vì admin thao tác trên khóa chính và slug thì sửa được.
 * <p>
 * <b>Không đặt regex hay ràng buộc nào lên {@code {slug}}.</b> Khi {@code /products/suggest} và
 * {@code /products/price-range} ra đời, Spring vẫn ưu tiên path literal hơn path template nên
 * không vỡ — thêm ràng buộc ở đây chỉ tạo ra thứ phải gỡ về sau.
 * <p>
 * Mọi {@code @RequestParam} / {@code @PathVariable} đều <b>khai tên tường minh</b>: dự án dùng BOM
 * {@code spring-boot-dependencies} chứ không dùng {@code spring-boot-starter-parent}, nên cờ
 * {@code -parameters} không được bật sẵn và tên tham số không còn trong bytecode.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    /** §A.4: trang đánh số từ 1. */
    private static final String DEFAULT_PAGE = "1";

    /** §A.4: mặc định 12 sản phẩm mỗi trang. */
    private static final String DEFAULT_LIMIT = "12";

    private static final String MESSAGE_PRODUCT_NOT_FOUND = "Không tìm thấy sản phẩm bạn đang tìm.";

    private final ProductAppService productAppService;

    /**
     * @param page trang, đánh số từ 1
     * @param limit số sản phẩm mỗi trang
     * @return {@code Paginated<Product>} theo §A.4
     */
    @GetMapping("/products")
    public PaginatedResponse<ProductResponse> getProducts(
            @RequestParam(name = "page", defaultValue = DEFAULT_PAGE) int page,
            @RequestParam(name = "limit", defaultValue = DEFAULT_LIMIT) int limit) {
        log.info("ProductController:->getProducts | page={} limit={}", page, limit);
        return productAppService.findProducts(page, limit);
    }

    /**
     * @param slug slug của sản phẩm
     * @return sản phẩm
     * @throws ProductNotFoundException khi slug không tồn tại hoặc sản phẩm đã bị xoá mềm
     */
    @GetMapping("/products/{slug}")
    public ProductResponse getProductBySlug(@PathVariable("slug") String slug) {
        log.info("ProductController:->getProductBySlug | slug={}", slug);
        ProductResponse product = productAppService.findProductBySlug(slug);
        if (product == null) {
            throw new ProductNotFoundException(MESSAGE_PRODUCT_NOT_FOUND);
        }
        return product;
    }

    /**
     * @param request body đã qua validate
     * @return sản phẩm vừa tạo, HTTP 201
     */
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        log.info("ProductController:->createProduct | slug={}", request.getSlug());
        return extractOrThrow(productAppService.createProduct(ProductControllerMapper.toCommand(request)));
    }

    /**
     * @param id khóa chính của sản phẩm
     * @param request body đã qua validate
     * @return sản phẩm sau khi sửa
     */
    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable("id") Long id,
                                         @Valid @RequestBody UpdateProductRequest request) {
        log.info("ProductController:->updateProduct | productId={} slug={}", id, request.getSlug());
        return extractOrThrow(productAppService.updateProduct(id, ProductControllerMapper.toCommand(request)));
    }

    /**
     * Xoá mềm — dòng vẫn nằm trong bảng với {@code is_active = false}.
     *
     * @param id khóa chính của sản phẩm
     * @throws ProductNotFoundException khi id không tồn tại hoặc sản phẩm đã bị xoá mềm từ trước
     */
    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable("id") Long id) {
        log.info("ProductController:->deleteProduct | productId={}", id);
        if (!productAppService.deleteProduct(id)) {
            throw new ProductNotFoundException(MESSAGE_PRODUCT_NOT_FOUND);
        }
    }

    /**
     * Dịch kết quả của tầng application thành payload hoặc exception.
     * <p>
     * Đây là chỗ duy nhất mã lỗi nghiệp vụ gặp mã HTTP: application không được biết HTTP, và kiểu
     * {@code *Exception} sống ở module controller (§3) nên application cũng không ném được chúng.
     *
     * @param result kết quả của lệnh ghi
     * @return sản phẩm khi thành công
     */
    private ProductResponse extractOrThrow(ProductMutationResponse result) {
        if (result.getProduct() != null) {
            return result.getProduct();
        }
        switch (result.getCode()) {
            case ProductMutationResponse.CODE_PRODUCT_NOT_FOUND:
                throw new ProductNotFoundException(result.getMessage());
            case ProductMutationResponse.CODE_DUPLICATE_SLUG:
                throw new DuplicateSlugException(result.getMessage());
            default:
                throw new InvalidProductDataException(result.getMessage());
        }
    }
}
