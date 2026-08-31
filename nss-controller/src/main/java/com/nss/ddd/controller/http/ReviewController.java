package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.ReviewMutationResponse;
import com.nss.ddd.application.model.response.ReviewResponse;
import com.nss.ddd.application.model.response.ReviewSummaryResponse;
import com.nss.ddd.application.service.review.ReviewAppService;
import com.nss.ddd.controller.dto.CreateReviewRequest;
import com.nss.ddd.controller.exception.DuplicateReviewException;
import com.nss.ddd.controller.exception.InvalidReviewDataException;
import com.nss.ddd.controller.exception.ProductNotFoundException;
import com.nss.ddd.controller.mapper.ReviewControllerMapper;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Biên REST của đánh giá sản phẩm — API_CONTRACT §B.8.
 * <p>
 * Trả DTO trần, <b>không bọc {@code ResultMessage}</b> (ADR 0001); thất bại dùng mã HTTP thật và
 * {@code ProblemDetail} do {@code GlobalExceptionHandler} dựng.
 * <p>
 * <b>Vì sao đây là một file RIÊNG chứ không phải ba method thêm vào {@link ProductController}.</b>
 * Ba đường dẫn ở đây nằm dưới {@code /api/products/...} nên chúng "thuộc về" sản phẩm theo URL —
 * nhưng {@code ProductController} là <b>chỉ đọc</b>, và javadoc của chính nó ghi thẳng
 * <i>"đừng thêm một đường ghi mới vào file này"</i> (backlog 0018, §C.4.3a). Luật đó tồn tại vì
 * backlog 0012 đã chứng minh việc "nhớ viết tay luật bảo mật" thất bại trong im lặng. Nhét một
 * {@code POST} vào đó để tiết kiệm một file là bào mòn luật bằng một ngoại lệ, và ngoại lệ thứ hai
 * sẽ rẻ hơn ngoại lệ thứ nhất.
 * <p>
 * <b>File này KHÔNG kèm theo một dòng nào trong {@code SecurityConfig}, và đó là điểm hay nhất của
 * ADR 0008 — không phải một chỗ bị quên.</b> Đo trên chính source:
 * <ul>
 *   <li>Hai {@code GET} đã được phủ bởi
 *       {@code requestMatchers(HttpMethod.GET, PATHS_PRODUCT_READ).permitAll()} —
 *       {@code PATHS_PRODUCT_READ} là {@code {"/api/products", "/api/products/**"}}.</li>
 *   <li>{@code POST} rơi vào {@code .anyRequest().authenticated()}, và <b>đó chính là luật ta
 *       muốn</b>: ADR 0008 chốt đánh giá phải có tài khoản.</li>
 * </ul>
 * Javadoc của {@code SecurityConfig} đã nói trước tình huống này: khi luật cần là "phải đăng nhập"
 * thì {@code anyRequest().authenticated()} đã phủ sẵn và file đó không phải sửa gì. Thêm một dòng
 * vào đấy là mở một cửa không ai định mở.
 * <p>
 * <b>Khoá là {@code id} SỐ, không phải {@code slug}</b> — khác §B.1 ({@code GET /products/{slug}}).
 * Hai cách đánh khoá cạnh nhau trên cùng một tài nguyên là chủ ý của hợp đồng; nhầm thì {@code 404},
 * tức hỏng ồn ào.
 * <p>
 * Mọi {@code @PathVariable} đều <b>khai tên tường minh</b>: dự án dùng BOM
 * {@code spring-boot-dependencies} chứ không dùng {@code spring-boot-starter-parent}, nên cờ
 * {@code -parameters} không được bật sẵn và tên tham số không còn trong bytecode.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Đánh giá sản phẩm",
        description = "Đọc và gửi đánh giá của một sản phẩm (§B.8). Hai đường đọc **công khai**; "
                + "gửi đánh giá **cần đăng nhập** và mỗi tài khoản chỉ đánh giá mỗi sản phẩm "
                + "**một lần**.")
public class ReviewController {

    private static final String MESSAGE_PRODUCT_NOT_FOUND = "Không tìm thấy sản phẩm bạn đang tìm.";

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Tên security scheme khai ở {@code OpenApiConfig}; phải khớp từng ký tự. */
    private static final String SECURITY_SCHEME = "bearerAuth";

    private final ReviewAppService reviewAppService;

    /**
     * @param productId khóa chính của sản phẩm
     * @return danh sách đánh giá, mới nhất trước
     * @throws ProductNotFoundException khi sản phẩm không tồn tại hoặc đã bị xoá mềm
     */
    @Operation(summary = "Danh sách đánh giá của một sản phẩm",
            description = """
                    Trả **mảng trần** `Review[]`, mới nhất trước — **không phân trang**. Hợp đồng \
                    khai `Review[]` chứ không phải `Paginated<Review>`.

                    - Khoá là **`id` số của sản phẩm**, không phải `slug` (khác `GET /products/{slug}`).
                    - Sản phẩm không tồn tại **hoặc đã bị xoá mềm** trả `404`, **không** trả mảng \
                    rỗng: mảng rỗng khiến giao diện hiện "chưa có đánh giá nào" cho một sản phẩm \
                    không tồn tại.
                    - Mỗi phần tử có đúng **6 trường**; `userId` là dữ liệu nội bộ và **không bao \
                    giờ** xuất hiện ở đây.
                    - `createdAt` là chuỗi ISO-8601 có hậu tố `Z`.""")
    @ApiResponse(responseCode = "200", description = "Đánh giá của sản phẩm; mảng rỗng khi chưa có đánh giá nào")
    @ApiResponse(responseCode = "404",
            description = "Sản phẩm không tồn tại hoặc đã bị xoá mềm; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/products/{id}/reviews")
    public List<ReviewResponse> getProductReviews(
            @Parameter(description = "Khoá chính (**số**) của sản phẩm.", example = "11")
            @PathVariable("id") Long productId) {
        log.info("ReviewController:->getProductReviews | productId={}", productId);
        List<ReviewResponse> reviews = reviewAppService.findReviews(productId);
        if (reviews == null) {
            throw new ProductNotFoundException(MESSAGE_PRODUCT_NOT_FOUND);
        }
        return reviews;
    }

    /**
     * @param productId khóa chính của sản phẩm
     * @return tổng hợp điểm đánh giá
     * @throws ProductNotFoundException khi sản phẩm không tồn tại hoặc đã bị xoá mềm
     */
    @Operation(summary = "Tổng hợp điểm đánh giá của một sản phẩm",
            description = """
                    Trả `ReviewSummary` — `average`, `total`, và `distribution` để vẽ biểu đồ \
                    phân bố sao.

                    - **`distribution` là object khoá chuỗi `'1'`…`'5'`**, không phải mảng 5 phần tử.
                    - **Cả năm khoá LUÔN có mặt**, kể cả mức sao không ai chọn — mức đó mang giá \
                    trị `0`. `GROUP BY` trần sẽ bỏ hẳn mức đó khỏi kết quả và biểu đồ sẽ rỗng mà \
                    không báo lỗi gì.
                    - **`average` làm tròn HALF-UP một chữ số thập phân**, đúng cùng quy ước với \
                    `product.rating` — hai con số này hiện cạnh nhau trên cùng một màn hình.
                    - Sản phẩm chưa có đánh giá nào: `average = 0.0`, `total = 0`, cả năm mức là `0`.
                    - Sản phẩm không tồn tại hoặc đã bị xoá mềm trả `404`.""")
    @ApiResponse(responseCode = "200", description = "Tổng hợp điểm đánh giá")
    @ApiResponse(responseCode = "404",
            description = "Sản phẩm không tồn tại hoặc đã bị xoá mềm; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/products/{id}/reviews/summary")
    public ReviewSummaryResponse getReviewSummary(
            @Parameter(description = "Khoá chính (**số**) của sản phẩm.", example = "11")
            @PathVariable("id") Long productId) {
        log.info("ReviewController:->getReviewSummary | productId={}", productId);
        ReviewSummaryResponse summary = reviewAppService.findSummary(productId);
        if (summary == null) {
            throw new ProductNotFoundException(MESSAGE_PRODUCT_NOT_FOUND);
        }
        return summary;
    }

    /**
     * Gửi một đánh giá mới — <b>cần access token</b> (ADR 0008).
     *
     * @param productId khóa chính của sản phẩm, lấy từ <b>path</b>
     * @param request body đã qua validate; trường {@code productId} trong body <b>bị bỏ qua</b>
     * @param jwt access token đã được filter chain giải mã; {@code sub} là id người dùng
     * @return đánh giá vừa ghi
     */
    @Operation(summary = "Gửi đánh giá cho một sản phẩm",
            description = """
                    Ghi một đánh giá mới rồi **tính lại `rating` và `reviewCount` của sản phẩm** \
                    (§C.3) trong cùng một transaction.

                    - **Cần access token.** Đây là chỗ backend **siết chặt hơn hợp đồng**: \
                    `API_CONTRACT.md` §B.8 khai endpoint này là công khai, [ADR \
                    0008](../decisions) chốt bắt token và **ADR thắng**. Lý do: đánh giá kéo \
                    `product.rating` đi — con số mà thẻ sản phẩm, bộ lọc `minRating` và biểu đồ \
                    trang chi tiết đều đọc — nên đó là *khuếch đại*, không chỉ là rác.
                    - **Mỗi tài khoản đánh giá mỗi sản phẩm đúng một lần**; lần thứ hai trả `409`.
                    - **`productId` lấy từ đường dẫn.** Trường cùng tên trong body **bị bỏ qua \
                    trong im lặng**, không báo lỗi.
                    - **`authorName` vẫn là tên tự khai** — nó là tên hiển thị, không phải danh \
                    tính; danh tính lấy từ claim `sub`. Hai thứ có thể khác nhau và backend không kiểm.
                    - **Phân biệt `422` với `409` bằng cấu trúc, không bằng mắt:** `422` kèm map \
                    **`errors`** (`tên trường → câu tiếng Việt`); `409` chỉ có `detail`, \
                    **không** có `errors`.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "201", description = "Đánh giá vừa ghi — đúng 6 trường")
    @ApiResponse(responseCode = "401",
            description = "Thiếu access token, token sai chữ ký hoặc đã hết hạn; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404",
            description = "Sản phẩm không tồn tại hoặc đã bị xoá mềm; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "Tài khoản này đã đánh giá sản phẩm này rồi; `detail` viết tiếng Việt và "
                    + "**không** kèm khoá `errors`",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Nội dung dưới 10 ký tự, số sao ngoài 1–5, hoặc thiếu trường bắt buộc. Kèm phần \
                    mở rộng **`errors`** — map `tên trường → thông điệp`.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/products/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse createReview(
            @Parameter(description = "Khoá chính (**số**) của sản phẩm. **Đây là nguồn chân lý** — "
                    + "trường `productId` trong body bị bỏ qua.", example = "11")
            @PathVariable("id") Long productId,
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        log.info("ReviewController:->createReview | productId={} userId={}", productId, userId);
        return extractOrThrow(reviewAppService.createReview(
                ReviewControllerMapper.toCommand(request, productId, userId)));
    }

    // ========== HELPERS ==========

    /**
     * Dịch kết quả của tầng application thành payload hoặc exception.
     * <p>
     * Đây là chỗ duy nhất mã lỗi nghiệp vụ gặp mã HTTP: application không được biết HTTP, và kiểu
     * {@code *Exception} sống ở module controller (§3) nên application cũng không ném được chúng.
     * Cùng khuôn với {@code OrderController.extractOrThrow}.
     * <p>
     * <b>Nhánh cuối rơi về 422</b> chứ không phải 409 hay 404: một mã lỗi mới ra đời mà quên khai ở
     * đây thì "dữ liệu không hợp lệ" là câu trả lời an toàn, còn "bạn đã đánh giá rồi" hay "không
     * tìm thấy sản phẩm" là những lời khẳng định <i>sai</i> về dữ liệu.
     *
     * @param result kết quả ghi đánh giá
     * @return đánh giá khi thành công
     */
    private ReviewResponse extractOrThrow(ReviewMutationResponse result) {
        if (result.getReview() != null) {
            return result.getReview();
        }
        if (ReviewMutationResponse.CODE_PRODUCT_NOT_FOUND.equals(result.getCode())) {
            throw new ProductNotFoundException(result.getMessage());
        }
        if (ReviewMutationResponse.CODE_DUPLICATE_REVIEW.equals(result.getCode())) {
            throw new DuplicateReviewException(result.getMessage());
        }
        throw new InvalidReviewDataException(result.getMessage());
    }
}
