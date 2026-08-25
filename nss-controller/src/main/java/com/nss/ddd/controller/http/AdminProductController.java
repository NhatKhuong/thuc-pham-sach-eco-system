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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
 * Biên REST của sản phẩm <b>trong khu quản trị</b> — API_CONTRACT §B.12.1, năm endpoint.
 * <p>
 * <b>Tách khỏi {@link ProductController} vì hai namespace được gác bằng hai lớp bảo mật khác
 * nhau</b>, đúng lý do §B.12.1 nêu cho việc frontend tách {@code adminProducts.api.ts} khỏi
 * {@code products.api.ts}: để chung một chỗ là mời một lời gọi ghi lọt ra ngoài hàng rào.
 * <p>
 * <b>Ba lệnh ghi ở đây CHUYỂN HẲN từ {@code /api/products} sang, không phải nhân bản.</b> Đây là
 * một thay đổi contract đã được Owner duyệt (backlog 0018): sau ticket này
 * {@code POST|PUT|DELETE /api/products} <b>không còn tồn tại</b>. Giữ cả hai đường là để hai cửa
 * vào cùng một chỗ ghi, mà một cửa nằm ngoài hàng rào {@code /api/admin/**} — đúng thứ §C.4.3a
 * dựng ra để chống.
 * <p>
 * <b>Không có {@code @PreAuthorize} nào trong file này, và sự vắng mặt đó là contract chứ không
 * phải thiếu sót.</b> §C.4.3a chốt: kiểm vai trò là <i>một</i> filter trên cả tiền tố
 * {@code /api/admin/**}, không rải rác từng handler — "một lần quên {@code @PreAuthorize} là rò dữ
 * liệu toàn bộ khách hàng". Hàng rào thật nằm ở {@code SecurityConfig#PATH_ADMIN_ALL}. Các
 * {@code @SecurityRequirement} và {@code 403} khai bên dưới chỉ để <i>tài liệu nói đúng sự thật
 * đó</i>; annotation của springdoc không tự cưỡng chế được gì.
 * <p>
 * <b>Đọc bằng {@code id}, không bằng {@code slug}</b> — khác hẳn {@code GET /products/{slug}} của
 * trang cửa hàng. Admin sửa được chính cái slug, nên đường dẫn màn sửa
 * ({@code /quan-tri/san-pham/:id/chinh-sua}) không được treo vào một trường có thể đổi: link đã lưu
 * sẽ hỏng ngay sau lần Lưu đầu tiên.
 * <p>
 * <b>Sản phẩm đã xoá mềm không bao giờ hiện ra ở đây.</b> Owner bên frontend đã chốt 2026-08-25:
 * sản phẩm xoá biến mất khỏi giao diện, khôi phục làm ở tầng cơ sở dữ liệu, và <i>không</i> có tham
 * số "xem hàng đã xoá". Vì vậy cả năm endpoint dùng chung quy ước xoá mềm của
 * {@code ProductRepository}: đã xoá thì hành xử như thể không tồn tại.
 * <p>
 * Mọi {@code @RequestParam} / {@code @PathVariable} đều <b>khai tên tường minh</b>: dự án dùng BOM
 * {@code spring-boot-dependencies} chứ không dùng {@code spring-boot-starter-parent}, nên cờ
 * {@code -parameters} không được bật sẵn và tên tham số không còn trong bytecode — bỏ tên đi thì
 * tài liệu hiện {@code arg0} mà endpoint vẫn trả 200.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@Tag(name = "Quản trị sản phẩm",
        description = "Thêm, sửa, xoá và tra cứu sản phẩm ở khu quản trị. "
                + "Toàn bộ nằm sau hàng rào `/api/admin/**` và **cần vai trò `ADMIN`**.")
public class AdminProductController {

    /** §A.4: trang đánh số từ 1. */
    private static final String DEFAULT_PAGE = "1";

    /** §A.4: mặc định 12 sản phẩm mỗi trang, khớp {@code PRODUCTS_PER_PAGE} của frontend. */
    private static final String DEFAULT_LIMIT = "12";

    private static final String MESSAGE_PRODUCT_NOT_FOUND = "Không tìm thấy sản phẩm bạn đang tìm.";

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Tên security scheme khai ở {@code OpenApiConfig} — nút *Authorize* của Swagger UI. */
    private static final String SECURITY_SCHEME = "bearerAuth";

    /** Mô tả 401 dùng chung cho cả năm endpoint — cùng một hàng rào, cùng một câu trả lời. */
    private static final String DESC_UNAUTHORIZED =
            "Không kèm access token, hoặc token sai chữ ký / đã hết hạn; `detail` viết tiếng Việt.";

    /** Mô tả 403 dùng chung cho cả năm endpoint — cùng một hàng rào, cùng một câu trả lời. */
    private static final String DESC_FORBIDDEN =
            "Đã đăng nhập nhưng tài khoản không có vai trò `ADMIN`; `detail` viết tiếng Việt. "
                    + "**Là `403`, không phải `401`** — `401` sẽ khiến client hiểu nhầm là token hết "
                    + "hạn rồi tự đăng xuất người dùng.";

    private final ProductAppService productAppService;

    /**
     * @param q từ khoá, khớp tên (bỏ dấu) hoặc slug
     * @param category slug danh mục, kéo theo cả danh mục con một cấp
     * @param stockStatus một trong {@code in_stock} / {@code low_stock} / {@code out_of_stock}
     * @param sort một trong năm giá trị của §B.12.1
     * @param page trang, đánh số từ 1
     * @param limit số sản phẩm mỗi trang
     * @return {@code Paginated<Product>} theo §A.4
     */
    @Operation(summary = "Danh sách sản phẩm cho bảng quản trị",
            description = """
                    Trả trang sản phẩm **đang bán** theo dạng phân trang chung của hệ \
                    (`items`, `total`, `page`, `limit`, `totalPages`). `total` là số dòng khớp \
                    **bộ lọc**, không phải tổng số sản phẩm.

                    Khác `GET /api/products` của trang cửa hàng ở hai chỗ: endpoint này **có lọc \
                    và có sắp xếp**, và nó **cần vai trò `ADMIN`**.

                    **`stockStatus` là một phân hoạch** — ba giá trị phủ kín và không chồng nhau, \
                    nên tổng số dòng của ba bộ lọc đúng bằng tổng số sản phẩm đang bán:
                    - `out_of_stock` — `stock <= 0`
                    - `low_stock` — `0 < stock <= 10`
                    - `in_stock` — `stock > 10` (**không phải `> 0`**)

                    **`category` lọc theo slug và kéo theo danh mục con một cấp**: lọc `rau-cu` ra \
                    cả sản phẩm thuộc `rau-an-la`. Slug không tồn tại cho ra **danh sách rỗng**, \
                    không phải "bỏ qua bộ lọc".

                    **`q` khớp tên (đã bỏ dấu) hoặc slug** — rộng hơn `q` của trang cửa hàng, vì \
                    slug là thứ admin trực tiếp sửa. Tìm kiếm không phân biệt dấu: `dau` khớp \
                    "Đậu Hà Lan".

                    **`sort` nhận đúng năm giá trị**, mặc định `newest`:
                    - `newest` — mới nhất trước
                    - `price_asc` / `price_desc` — theo **giá sau giảm**, không theo `price`
                    - `best_selling` — đã bán nhiều nhất trước
                    - `rating` — điểm đánh giá cao nhất trước

                    Giá trị lạ ở `sort` và `stockStatus` **không gây lỗi**: `sort` rơi về `newest`, \
                    `stockStatus` coi như không lọc.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Trang sản phẩm; danh sách rỗng khi không có dòng nào khớp")
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public PaginatedResponse<ProductResponse> getAdminProducts(
            @Parameter(description = "Từ khoá; khớp **tên đã bỏ dấu** hoặc **slug**.", example = "ca rot")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Slug danh mục; kéo theo cả danh mục con một cấp.", example = "rau-cu")
            @RequestParam(name = "category", required = false) String category,
            @Parameter(description = "`in_stock` | `low_stock` | `out_of_stock`.", example = "low_stock")
            @RequestParam(name = "stockStatus", required = false) String stockStatus,
            @Parameter(description = "`newest` | `price_asc` | `price_desc` | `best_selling` | `rating`.",
                    example = "newest")
            @RequestParam(name = "sort", required = false) String sort,
            @Parameter(description = "Trang cần lấy, **đánh số từ 1**. Mặc định `1`.", example = "1")
            @RequestParam(name = "page", defaultValue = DEFAULT_PAGE) int page,
            @Parameter(description = "Số sản phẩm mỗi trang. Mặc định `12`.", example = "12")
            @RequestParam(name = "limit", defaultValue = DEFAULT_LIMIT) int limit) {
        log.info("AdminProductController:->getAdminProducts | q={} category={} stockStatus={} sort={} page={} limit={}",
                q, category, stockStatus, sort, page, limit);
        return productAppService.findAdminProducts(
                ProductControllerMapper.toFilter(q, category, stockStatus, sort, page, limit));
    }

    /**
     * @param id khóa chính của sản phẩm
     * @return sản phẩm
     * @throws ProductNotFoundException khi id không tồn tại hoặc sản phẩm đã bị xoá mềm
     */
    @Operation(summary = "Chi tiết một sản phẩm theo id",
            description = """
                    Tra sản phẩm bằng **`id`** (khóa chính), **không** bằng `slug` — khác hẳn \
                    `GET /api/products/{slug}` của trang cửa hàng.

                    Lý do là link màn sửa phải sống sót qua lần Lưu đầu tiên: admin sửa được chính \
                    cái slug, nên một đường dẫn treo vào slug sẽ hỏng ngay sau đó.

                    Sản phẩm đã bị xoá mềm coi như không tồn tại và trả `404`.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Sản phẩm khớp id")
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404",
            description = "Id không tồn tại hoặc sản phẩm đã bị xoá mềm; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public ProductResponse getAdminProduct(
            @Parameter(description = "Khóa chính của sản phẩm.", example = "1")
            @PathVariable("id") Long id) {
        log.info("AdminProductController:->getAdminProduct | productId={}", id);
        ProductResponse product = productAppService.findProductById(id);
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

                    **`slug` bỏ trống thì backend tự sinh từ `name`** (bỏ dấu, `đ` thành `d`, nối \
                    bằng gạch ngang). **Slug có gửi lên cũng được chuẩn hoá theo đúng cách đó** — \
                    gõ `Cà Rốt Hữu Cơ` vào ô slug cho ra `ca-rot-huu-co`, không phải một lỗi.

                    **Trùng slug trả `409`, và backend KHÔNG tự thêm hậu tố `-1`.** Slug đi thẳng \
                    lên URL công khai; một slug lặng lẽ khác thứ admin vừa gõ sẽ phá đúng cái link \
                    họ chuẩn bị chia sẻ. Phép kiểm trùng tính cả sản phẩm **đã xoá mềm**, vì ràng \
                    buộc duy nhất nằm trên toàn bảng.

                    - Tiền (`price`, `salePrice`) là **số nguyên VNĐ**.
                    - Bỏ trống `salePrice` (hoặc gửi `null`) nghĩa là không giảm giá; có giá trị \
                    thì phải **nhỏ hơn** `price`.
                    - `images` là các đường dẫn **tương đối** `/images/...`; thứ tự trong mảng là \
                    thứ tự hiển thị.
                    - Các trường hệ thống (`id`, `effectivePrice`, `rating`, `reviewCount`, \
                    `sold`, `isActive`, `createdAt`, `updatedAt`) client gửi lên sẽ bị **bỏ qua \
                    trong im lặng** (§C.3).""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "201", description = "Sản phẩm đã được tạo")
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "Slug đã có sản phẩm khác giữ; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Dữ liệu không hợp lệ. Kèm phần mở rộng **`errors`** — map `tên trường → thông \
                    điệp` (thông điệp validate hiện viết tiếng Anh); `detail` viết tiếng Việt. \
                    Cũng dùng cho vi phạm quy tắc nghiệp vụ: `salePrice` không nhỏ hơn `price`, \
                    `categoryId` / `brandId` không tồn tại, hoặc không sinh được slug từ `name`.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        log.info("AdminProductController:->createProduct | slug={}", request.getSlug());
        return extractOrThrow(productAppService.createProduct(ProductControllerMapper.toCommand(request)));
    }

    /**
     * @param id khóa chính của sản phẩm
     * @param request body đã qua validate
     * @return sản phẩm sau khi sửa
     */
    @Operation(summary = "Sửa toàn bộ một sản phẩm",
            description = """
                    Ghi đè sản phẩm theo **`id`** (khóa chính), không theo `slug` — admin thao tác \
                    trên id vì slug thì sửa được.

                    **Giữ nguyên slug cũ KHÔNG phải `409`.** Phép kiểm trùng loại trừ chính sản \
                    phẩm đang sửa, nên lưu lại một sản phẩm mà không đụng gì tới slug vẫn trả `200`.

                    Áp dụng cùng quy ước với lệnh tạo: slug được chuẩn hoá, tiền là số nguyên VNĐ, \
                    `salePrice = null` nghĩa là không giảm giá, ảnh là đường dẫn tương đối. **Mảng \
                    `images` thay trọn mảng cũ** — đây là `PUT`, không phải `PATCH`.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Sản phẩm sau khi sửa")
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
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
    @PutMapping("/{id}")
    public ProductResponse updateProduct(
            @Parameter(description = "Khóa chính của sản phẩm cần sửa.", example = "1")
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        log.info("AdminProductController:->updateProduct | productId={} slug={}", id, request.getSlug());
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
                    Đánh dấu sản phẩm ngừng bán (`is_active = false`) — **dòng vẫn nằm trong \
                    bảng**, không xoá vật lý. Đơn hàng cũ và đánh giá cũ vẫn tham chiếu được tới nó.

                    Thành công trả **`204`** với **thân rỗng**. Xoá lại một sản phẩm đã xoá mềm trả \
                    `404`. Sau khi xoá, sản phẩm biến mất khỏi **cả** `GET /api/products` công khai \
                    **lẫn** danh sách quản trị.

                    **Không có đường khôi phục từ giao diện** (Owner chốt 2026-08-25); khôi phục \
                    làm ở tầng cơ sở dữ liệu. Lưu ý slug của sản phẩm đã xoá **vẫn giữ chỗ** — \
                    ràng buộc duy nhất nằm trên toàn bảng, không quan tâm `is_active`.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "204", description = "Đã xoá mềm; không có thân phản hồi",
            content = @Content)
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404",
            description = "Id không tồn tại hoặc sản phẩm đã bị xoá mềm từ trước; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @Parameter(description = "Khóa chính của sản phẩm cần xoá mềm.", example = "1")
            @PathVariable("id") Long id) {
        log.info("AdminProductController:->deleteProduct | productId={}", id);
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
