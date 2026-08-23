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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
 * không vỡ — thêm ràng buộc ở đây chỉ tạo ra thứ phải gỡ về sau. {@code @Parameter} bên dưới chỉ
 * <i>mô tả</i>, cố ý không <i>ràng buộc</i>, vì đúng lý do đó.
 * <p>
 * Mọi {@code @RequestParam} / {@code @PathVariable} đều <b>khai tên tường minh</b>: dự án dùng BOM
 * {@code spring-boot-dependencies} chứ không dùng {@code spring-boot-starter-parent}, nên cờ
 * {@code -parameters} không được bật sẵn và tên tham số không còn trong bytecode. Springdoc đọc
 * đúng các tên khai ở đây — bỏ chúng đi thì tài liệu hiện {@code arg0} mà vẫn trả 200.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Sản phẩm",
        description = "Đọc và quản trị sản phẩm. Đọc theo `slug`, ghi theo `id`.")
public class ProductController {

    /** §A.4: trang đánh số từ 1. */
    private static final String DEFAULT_PAGE = "1";

    /** §A.4: mặc định 12 sản phẩm mỗi trang. */
    private static final String DEFAULT_LIMIT = "12";

    private static final String MESSAGE_PRODUCT_NOT_FOUND = "Không tìm thấy sản phẩm bạn đang tìm.";

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    private final ProductAppService productAppService;

    /**
     * @param page trang, đánh số từ 1
     * @param limit số sản phẩm mỗi trang
     * @return {@code Paginated<Product>} theo §A.4
     */
    @Operation(summary = "Danh sách sản phẩm có phân trang",
            description = """
                    Trả về trang sản phẩm đang bán theo dạng phân trang chung của hệ \
                    (`items`, `total`, `page`, `limit`, `totalPages`).

                    - **`page` đánh số từ 1**, không phải từ 0; backend tự trừ 1 khi dựng `Pageable`.
                    - `limit` mặc định `12`.
                    - `price` và `salePrice` là **số nguyên VNĐ**; `effectivePrice` \
                    (`salePrice ?? price`) cũng vậy nhưng **client tự tính**, response không trả.
                    - `salePrice` bằng `null` nghĩa là **không giảm giá** (không dùng `0` hay chuỗi rỗng).
                    - `createdAt` là chuỗi ISO-8601 có hậu tố `Z`; ảnh là đường dẫn **tương đối** `/images/...`.

                    Ticket này chưa dựng lọc / tìm kiếm / sắp xếp — chỉ có `page` và `limit`.""")
    @ApiResponse(responseCode = "200", description = "Trang sản phẩm; danh sách rỗng khi vượt quá trang cuối")
    @GetMapping("/products")
    public PaginatedResponse<ProductResponse> getProducts(
            @Parameter(description = "Trang cần lấy, **đánh số từ 1**. Mặc định `1`.", example = "1")
            @RequestParam(name = "page", defaultValue = DEFAULT_PAGE) int page,
            @Parameter(description = "Số sản phẩm mỗi trang. Mặc định `12`.", example = "12")
            @RequestParam(name = "limit", defaultValue = DEFAULT_LIMIT) int limit) {
        log.info("ProductController:->getProducts | page={} limit={}", page, limit);
        return productAppService.findProducts(page, limit);
    }

    /**
     * @param slug slug của sản phẩm
     * @return sản phẩm
     * @throws ProductNotFoundException khi slug không tồn tại hoặc sản phẩm đã bị xoá mềm
     */
    @Operation(summary = "Chi tiết một sản phẩm theo slug",
            description = """
                    Tra sản phẩm bằng `slug` — chuỗi không dấu nối bằng gạch ngang mà frontend \
                    dựng URL từ đó.

                    Sản phẩm đã bị xoá mềm coi như không tồn tại và trả `404`.""")
    @ApiResponse(responseCode = "200", description = "Sản phẩm khớp slug")
    @ApiResponse(responseCode = "404",
            description = "Slug không tồn tại hoặc sản phẩm đã bị xoá mềm; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/products/{slug}")
    public ProductResponse getProductBySlug(
            @Parameter(description = "Slug của sản phẩm, ví dụ `ca-rot-huu-co`.", example = "ca-rot-huu-co")
            @PathVariable("slug") String slug) {
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
    @Operation(summary = "Tạo sản phẩm mới",
            description = """
                    Tạo một sản phẩm và trả về **`201`** kèm sản phẩm vừa tạo.

                    - Tiền (`price`, `salePrice`) là **số nguyên VNĐ**.
                    - Bỏ trống `salePrice` (hoặc gửi `null`) nghĩa là không giảm giá.
                    - `images` là các đường dẫn **tương đối** `/images/...`; thứ tự trong mảng là \
                    thứ tự hiển thị.
                    - Các trường hệ thống (`id`, `effectivePrice`, `rating`, `reviewCount`, `sold`, \
                    `isActive`, `createdAt`, `updatedAt`) client gửi lên sẽ bị bỏ qua trong im lặng.""")
    @ApiResponse(responseCode = "201", description = "Sản phẩm đã được tạo")
    @ApiResponse(responseCode = "409",
            description = "Slug đã có sản phẩm khác giữ; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Dữ liệu không hợp lệ. Kèm phần mở rộng **`errors`** — map `tên trường → thông \
                    điệp` (thông điệp validate hiện viết tiếng Anh); `detail` viết tiếng Việt. \
                    Cũng dùng cho vi phạm quy tắc nghiệp vụ như `salePrice` không nhỏ hơn `price`, \
                    hoặc `categoryId` / `brandId` không tồn tại.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
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
    @Operation(summary = "Sửa toàn bộ một sản phẩm",
            description = """
                    Ghi đè sản phẩm theo **`id`** (khóa chính), không phải theo `slug` — admin thao \
                    tác trên id vì slug thì sửa được.

                    Áp dụng cùng quy ước kiểu dữ liệu với lệnh tạo: tiền là số nguyên VNĐ, \
                    `salePrice = null` nghĩa là không giảm giá, ảnh là đường dẫn tương đối.""")
    @ApiResponse(responseCode = "200", description = "Sản phẩm sau khi sửa")
    @ApiResponse(responseCode = "404",
            description = "Id không tồn tại hoặc sản phẩm đã bị xoá mềm; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "Slug mới đã có sản phẩm khác giữ; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Dữ liệu không hợp lệ. Kèm phần mở rộng **`errors`** — map `tên trường → thông \
                    điệp`; `detail` viết tiếng Việt.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(
            @Parameter(description = "Khóa chính của sản phẩm cần sửa.", example = "1")
            @PathVariable("id") Long id,
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
    @Operation(summary = "Xoá mềm một sản phẩm",
            description = """
                    Đánh dấu sản phẩm ngừng bán (`is_active = false`) — **dòng vẫn nằm trong bảng**, \
                    không xoá vật lý.

                    Thành công trả **`204`** với **thân rỗng**. Xoá lại một sản phẩm đã xoá mềm \
                    trả `404`.""")
    @ApiResponse(responseCode = "204", description = "Đã xoá mềm; không có thân phản hồi",
            content = @Content)
    @ApiResponse(responseCode = "404",
            description = "Id không tồn tại hoặc sản phẩm đã bị xoá mềm từ trước; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @Parameter(description = "Khóa chính của sản phẩm cần xoá mềm.", example = "1")
            @PathVariable("id") Long id) {
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
