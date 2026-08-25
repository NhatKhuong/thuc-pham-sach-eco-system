package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.application.service.product.ProductAppService;
import com.nss.ddd.controller.exception.ProductNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biên REST của sản phẩm — API_CONTRACT §B.1.
 * <p>
 * Trả DTO trần, <b>không bọc {@code ResultMessage}</b> (ADR 0001, giống {@code HelloController});
 * thất bại dùng mã HTTP thật và {@code ProblemDetail} do {@code GlobalExceptionHandler} dựng.
 * <p>
 * <b>CHỈ ĐỌC, và chỉ công khai.</b> Đọc theo {@code slug} vì frontend dựng URL từ slug.
 * <p>
 * <b>Ba lệnh ghi từng nằm ở file này đã CHUYỂN sang {@link AdminProductController}</b> tại backlog
 * 0018 — {@code POST|PUT|DELETE /api/products} không còn tồn tại. Là một thay đổi contract, Owner
 * đã duyệt. Chúng chuyển hẳn chứ không nhân bản: giữ lại một bản ở đây là để hai cửa vào cùng một
 * chỗ ghi mà một cửa nằm ngoài hàng rào {@code /api/admin/**}, đúng thứ §C.4.3a dựng ra để chống.
 * <p>
 * <b>Vì vậy đừng thêm một đường ghi mới vào file này.</b> Nơi của nó là namespace quản trị, nơi
 * hàng rào đã có sẵn và phủ trước cả những endpoint chưa ra đời. Thêm vào đây thì luật tương ứng
 * phải được nhớ viết tay ở {@code SecurityConfig} — và backlog 0012 đã chứng minh việc "nhớ viết
 * tay" thất bại trong im lặng như thế nào.
 * <p>
 * Việc endpoint nào cần quyền gì là quyết định của {@code SecurityConfig}, không phải của file
 * này — nơi duy nhất quyết định điều đó.
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
        description = "Đọc sản phẩm ở trang cửa hàng — công khai, tra theo `slug`. "
                + "Thêm / sửa / xoá nằm ở nhóm **Quản trị sản phẩm** (`/api/admin/products`).")
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
}
