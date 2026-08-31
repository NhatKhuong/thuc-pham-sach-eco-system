package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.model.response.PriceRangeResponse;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.application.service.product.ProductAppService;
import com.nss.ddd.controller.exception.InvalidFilterValueException;
import com.nss.ddd.controller.exception.ProductNotFoundException;
import com.nss.ddd.controller.mapper.ProductControllerMapper;
import com.nss.ddd.domain.model.ProductSort;

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

import java.math.BigDecimal;
import java.util.List;

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

    /** §B.1: mặc định 4 sản phẩm liên quan. */
    private static final String DEFAULT_RELATED_LIMIT = "4";

    /** §B.1: mặc định 5 gợi ý tìm kiếm. */
    private static final String DEFAULT_SUGGEST_LIMIT = "5";

    private static final String MESSAGE_PRODUCT_NOT_FOUND = "Không tìm thấy sản phẩm bạn đang tìm.";

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    private final ProductAppService productAppService;

    /**
     * @param q từ khoá, khớp {@code name} hoặc {@code shortDescription} (khác {@code q} của admin)
     * @param category slug danh mục, kéo theo cả danh mục con một cấp
     * @param minPrice giá thấp nhất theo {@code effectivePrice}
     * @param maxPrice giá cao nhất theo {@code effectivePrice}
     * @param minRating điểm đánh giá thấp nhất
     * @param inStockOnly chỉ hiện còn hàng
     * @param onSaleOnly chỉ hiện đang giảm giá
     * @param isFeatured chỉ hiện nổi bật
     * @param isBestSeller chỉ hiện bán chạy
     * @param sort một trong năm giá trị của §B.1
     * @param page trang, đánh số từ 1
     * @param limit số sản phẩm mỗi trang
     * @return {@code Paginated<Product>} theo §A.4
     * @throws InvalidFilterValueException khi {@code sort} có giá trị không nhận ra (ADR 0007 vế 1)
     */
    @Operation(operationId = "getProducts", summary = "Danh sách sản phẩm có lọc, sắp xếp và phân trang",
            description = """
                    Trả về trang sản phẩm đang bán theo dạng phân trang chung của hệ \
                    (`items`, `total`, `page`, `limit`, `totalPages`).

                    **Chú ý cho người đọc tài liệu này:** cùng đường dẫn `/products` còn có một \
                    biến thể `GET /products?ids=1,2,3` trả `Product[]` phẳng, không phân trang — \
                    xem endpoint riêng "Nhiều sản phẩm theo danh sách id" bên dưới. Springdoc gộp \
                    tham số của cả hai vào cùng một mục vì OpenAPI không phân biệt được hai toán tử \
                    trên cùng một cặp (path, method); hành vi thật thì tách bạch — có `ids` đi theo \
                    nhánh kia, không có `ids` thì đi theo nhánh này.

                    - **`page` đánh số từ 1**, không phải từ 0; backend tự trừ 1 khi dựng `Pageable`.
                    - `limit` mặc định `12`.
                    - **Lọc và sắp xếp theo giá đều dùng `effectivePrice` (`salePrice ?? price`)**, \
                    KHÔNG dùng `price` — một sản phẩm giá gốc 600k đang giảm còn 400k phải lọt vào \
                    bộ lọc `maxPrice=500000`.
                    - `q` khớp `name` **hoặc** `shortDescription`, bỏ dấu, không phân biệt hoa \
                    thường — **khác** `q` của khu quản trị (khớp `name` hoặc `slug`).
                    - `category` lọc theo slug và kéo theo danh mục con **một cấp**; slug không tồn \
                    tại cho ra **tập rỗng** (ADR 0007), không phải cả cửa hàng.
                    - Bốn cờ `inStockOnly`/`onSaleOnly`/`isFeatured`/`isBestSeller`: `true` kích \
                    hoạt bộ lọc tương ứng, vắng mặt hoặc `false` là không lọc.
                    - **`sort` nhận đúng năm giá trị** — `newest` (mặc định) · `price_asc` · \
                    `price_desc` · `best_selling` · `rating`. Giá trị lạ (khác rỗng, không khớp \
                    năm giá trị) trả **`422`** kèm `errors.sort` liệt kê giá trị hợp lệ — **khác** \
                    `sort` của khu quản trị, vốn khoan dung và rơi về `newest`.
                    - `price` và `salePrice` là **số nguyên VNĐ**; `effectivePrice` \
                    (`salePrice ?? price`) cũng vậy nhưng **client tự tính**, response không trả.
                    - `salePrice` bằng `null` nghĩa là **không giảm giá** (không dùng `0` hay chuỗi rỗng).
                    - `createdAt` là chuỗi ISO-8601 có hậu tố `Z`; ảnh là đường dẫn **tương đối** `/images/...`.
                    - Sản phẩm đã xoá mềm không bao giờ lọt vào kết quả.""")
    @ApiResponse(responseCode = "200", description = "Trang sản phẩm; danh sách rỗng khi không có dòng nào khớp")
    @ApiResponse(responseCode = "422",
            description = "`sort` có giá trị không nhận ra. Kèm map `errors` (khoá `sort`); "
                    + "`detail` liệt kê giá trị hợp lệ, viết tiếng Việt.",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/products")
    public PaginatedResponse<ProductResponse> getProducts(
            @Parameter(description = "Từ khoá; khớp **tên** hoặc **mô tả ngắn**.", example = "cam")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Slug danh mục; kéo theo cả danh mục con một cấp.", example = "rau-cu")
            @RequestParam(name = "category", required = false) String category,
            @Parameter(description = "Giá thấp nhất (VNĐ), theo giá sau giảm.", example = "10000")
            @RequestParam(name = "minPrice", required = false) Long minPrice,
            @Parameter(description = "Giá cao nhất (VNĐ), theo giá sau giảm.", example = "500000")
            @RequestParam(name = "maxPrice", required = false) Long maxPrice,
            @Parameter(description = "Điểm đánh giá thấp nhất, thang 0.0-5.0.", example = "4")
            @RequestParam(name = "minRating", required = false) BigDecimal minRating,
            @Parameter(description = "Chỉ hiện sản phẩm còn hàng.", example = "true")
            @RequestParam(name = "inStockOnly", required = false) Boolean inStockOnly,
            @Parameter(description = "Chỉ hiện sản phẩm đang giảm giá.", example = "true")
            @RequestParam(name = "onSaleOnly", required = false) Boolean onSaleOnly,
            @Parameter(description = "Chỉ hiện sản phẩm nổi bật.", example = "true")
            @RequestParam(name = "isFeatured", required = false) Boolean isFeatured,
            @Parameter(description = "Chỉ hiện sản phẩm bán chạy.", example = "true")
            @RequestParam(name = "isBestSeller", required = false) Boolean isBestSeller,
            @Parameter(description = "`newest` | `price_asc` | `price_desc` | `best_selling` | `rating`.",
                    schema = @Schema(allowableValues = {"newest", "price_asc", "price_desc",
                            "best_selling", "rating"}), example = "newest")
            @RequestParam(name = "sort", required = false) String sort,
            @Parameter(description = "Trang cần lấy, **đánh số từ 1**. Mặc định `1`.", example = "1")
            @RequestParam(name = "page", defaultValue = DEFAULT_PAGE) int page,
            @Parameter(description = "Số sản phẩm mỗi trang. Mặc định `12`.", example = "12")
            @RequestParam(name = "limit", defaultValue = DEFAULT_LIMIT) int limit) {
        log.info("ProductController:->getProducts | q={} category={} minPrice={} maxPrice={} minRating={} "
                        + "inStockOnly={} onSaleOnly={} isFeatured={} isBestSeller={} sort={} page={} limit={}",
                q, category, minPrice, maxPrice, minRating, inStockOnly, onSaleOnly, isFeatured, isBestSeller,
                sort, page, limit);
        return productAppService.findProducts(ProductControllerMapper.toPublicFilter(q, category, minPrice,
                maxPrice, minRating, inStockOnly, onSaleOnly, isFeatured, isBestSeller, sort, page, limit));
    }

    /**
     * @param ids các khóa chính cần tra, dạng {@code 1,2,3}
     * @return sản phẩm còn hiệu lực khớp {@code ids}
     */
    @Operation(operationId = "getProductsByIds", summary = "Nhiều sản phẩm theo danh sách id",
            description = """
                    Tra nhiều sản phẩm cùng lúc theo id, dùng cho giỏ hàng đã lưu trong \
                    `localStorage` (chỉ giữ id, không giữ dữ liệu hiển thị).

                    **Cùng đường dẫn `/products`, phân biệt bởi sự có mặt của `ids`** — có `ids` thì \
                    trả `Product[]` phẳng, không phân trang; không có `ids` thì rơi vào \
                    `GET /products` thường (có lọc, có phân trang).

                    **`ids` là tham số tập mở (ADR 0007):** id không tồn tại hoặc đã bị xoá mềm thì \
                    lặng lẽ vắng mặt khỏi kết quả — không lỗi, không phần tử giữ chỗ. Token không \
                    phải số cũng bị bỏ qua theo cùng cách. Thứ tự kết quả **không đảm bảo** khớp \
                    thứ tự `ids`; client tự đánh chỉ mục theo `id`.""")
    @ApiResponse(responseCode = "200", description = "Sản phẩm khớp ids; rỗng khi không id nào khớp")
    @GetMapping(value = "/products", params = "ids")
    public List<ProductResponse> getProductsByIds(
            @Parameter(description = "Danh sách id, phân tách bằng dấu phẩy.", example = "1,2,3")
            @RequestParam(name = "ids") String ids) {
        List<Long> parsed = ProductControllerMapper.toIdList(ids);
        log.info("ProductController:->getProductsByIds | ids={}", ids);
        return productAppService.findProductsByIds(parsed);
    }

    /**
     * @param slug slug của sản phẩm gốc
     * @param limit số sản phẩm tối đa, mặc định 4
     * @return sản phẩm liên quan
     * @throws ProductNotFoundException khi {@code slug} không tồn tại hoặc sản phẩm đã bị xoá mềm
     */
    @Operation(summary = "Sản phẩm liên quan",
            description = """
                    Gợi ý sản phẩm cùng danh mục với sản phẩm gốc, loại trừ chính nó — hiển thị ở \
                    cuối trang chi tiết sản phẩm.

                    Thứ tự cố định (bán chạy trước, rồi tới đánh giá cao), **không** nhận tham số \
                    `sort`. `limit` mặc định `4`.""")
    @ApiResponse(responseCode = "200", description = "Sản phẩm liên quan; rỗng khi không có sản phẩm nào cùng danh mục")
    @ApiResponse(responseCode = "404",
            description = "Slug của sản phẩm gốc không tồn tại hoặc đã bị xoá mềm; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/products/{slug}/related")
    public List<ProductResponse> getRelatedProducts(
            @Parameter(description = "Slug của sản phẩm gốc.", example = "ca-rot-huu-co")
            @PathVariable("slug") String slug,
            @Parameter(description = "Số sản phẩm tối đa. Mặc định `4`.", example = "4")
            @RequestParam(name = "limit", defaultValue = DEFAULT_RELATED_LIMIT) int limit) {
        log.info("ProductController:->getRelatedProducts | slug={} limit={}", slug, limit);
        List<ProductResponse> related = productAppService.findRelatedProducts(slug, limit);
        if (related == null) {
            throw new ProductNotFoundException(MESSAGE_PRODUCT_NOT_FOUND);
        }
        return related;
    }

    /**
     * @param q từ khoá gõ dở
     * @param limit số gợi ý tối đa, mặc định 5
     * @return gợi ý tìm kiếm
     */
    @Operation(summary = "Gợi ý tìm kiếm",
            description = """
                    Gợi ý sản phẩm khi khách gõ vào ô tìm kiếm — cùng phạm vi khớp với `q` của \
                    `GET /products` (`name` hoặc `shortDescription`, bỏ dấu). `limit` mặc định `5`.

                    `q` rỗng hoặc vắng mặt trả danh sách rỗng, không phải toàn bộ sản phẩm.""")
    @ApiResponse(responseCode = "200", description = "Gợi ý tìm kiếm; rỗng khi không khớp sản phẩm nào")
    @GetMapping("/products/suggest")
    public List<ProductResponse> searchSuggestions(
            @Parameter(description = "Từ khoá gõ dở.", example = "ca r")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Số gợi ý tối đa. Mặc định `5`.", example = "5")
            @RequestParam(name = "limit", defaultValue = DEFAULT_SUGGEST_LIMIT) int limit) {
        log.info("ProductController:->searchSuggestions | q={} limit={}", q, limit);
        return productAppService.findSuggestions(q, limit);
    }

    /**
     * @return khoảng giá {@code { min, max }}
     */
    @Operation(summary = "Khoảng giá của mọi sản phẩm đang bán",
            description = """
                    Trả `{ min, max }` theo `effectivePrice` (`salePrice ?? price`) trên mọi sản \
                    phẩm còn hiệu lực — dùng để dựng thanh trượt giá ở bộ lọc.

                    `min`/`max` là `null` khi cửa hàng chưa có sản phẩm nào đang bán.""")
    @ApiResponse(responseCode = "200", description = "Khoảng giá")
    @GetMapping("/products/price-range")
    public PriceRangeResponse getPriceRange() {
        log.info("ProductController:->getPriceRange");
        return productAppService.findPriceRange();
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
