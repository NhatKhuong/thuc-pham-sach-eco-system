package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.model.response.OrderResponse;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.service.order.OrderAppService;
import com.nss.ddd.controller.dto.UpdateOrderStatusRequest;
import com.nss.ddd.controller.exception.InvalidOrderDataException;
import com.nss.ddd.controller.exception.OrderNotFoundException;
import com.nss.ddd.controller.mapper.OrderControllerMapper;

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

import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biên REST của đơn hàng <b>trong khu quản trị</b> — API_CONTRACT §B.12.2, ba endpoint.
 * <p>
 * <b>Tách khỏi {@link OrderController} vì hai namespace được gác bằng hai lớp bảo mật khác nhau</b>
 * — cùng lý do đã tách {@link AdminProductController} khỏi {@code ProductController}, và cùng lý do
 * frontend tách {@code adminOrders.api.ts} khỏi {@code orders.api.ts}. Để chung một file là mời một
 * lời gọi liệt kê chéo lọt ra ngoài hàng rào.
 * <p>
 * <b>{@code GET /admin/orders} là SONG SINH của {@code GET /orders/me}, và việc hai endpoint cùng
 * tồn tại chính là cách giữ §C.4.1 không bị nới lỏng.</b> Ở đây {@code userId} là một <i>bộ lọc</i>
 * hợp lệ (§C.4.3b); ở kia nó là <i>danh tính</i> lấy từ claim {@code sub} và không bao giờ đến từ
 * query. Vì có endpoint này nên {@code /orders/me} không bao giờ cần mọc thêm {@code ?userId=}.
 * <p>
 * <b>Không có {@code @PreAuthorize} nào trong file này, và sự vắng mặt đó là contract chứ không
 * phải thiếu sót.</b> §C.4.3a chốt: kiểm vai trò là <i>một</i> filter trên cả tiền tố
 * {@code /api/admin/**}. Hàng rào thật nằm ở {@code SecurityConfig#PATH_ADMIN_ALL} — <b>và nó đã
 * gác ba đường dẫn này từ trước khi chúng tồn tại</b>, kể cả {@code PATCH}: dòng luật đó cố ý không
 * khai {@code HttpMethod}. Các {@code @SecurityRequirement} và {@code 403} bên dưới chỉ để tài liệu
 * nói đúng sự thật đó.
 * <p>
 * <b>Khoá theo {@code code}, không phải {@code id} — ngoại lệ có chủ ý so với luật "admin khoá theo
 * id" của {@link AdminProductController}.</b> Lý do khác nhau ở hai chỗ: slug của sản phẩm thì admin
 * sửa được nên link màn sửa không được treo vào nó, còn mã đơn thì <i>bất biến</i> và là thứ duy
 * nhất nhân viên với khách cùng đọc được qua điện thoại. Nó cũng khớp URL
 * {@code /quan-tri/don-hang/:code} và khớp {@code GET /orders/{code}} sẵn có.
 * <p>
 * <b>Không có endpoint xoá đơn, và cũng không được mở</b> (§B.12.2). Đơn đã đặt là chứng từ; sửa
 * items hoặc tiền của đơn cũng vậy — số tiền trên đơn là bản chụp tại thời điểm đặt (§C.1), sửa về
 * sau là làm lệch chính thứ khách đã trả. Đó là lý do endpoint duy nhất có thân request ở đây chỉ
 * nhận <i>một</i> trường.
 * <p>
 * Mọi {@code @RequestParam} / {@code @PathVariable} đều <b>khai tên tường minh</b>: dự án dùng BOM
 * {@code spring-boot-dependencies} chứ không dùng {@code spring-boot-starter-parent}, nên cờ
 * {@code -parameters} không được bật sẵn — bỏ tên đi thì tài liệu hiện {@code arg0} mà endpoint vẫn
 * trả 200.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Quản trị đơn hàng",
        description = "Liệt kê chéo mọi người dùng, tra theo mã, và đổi trạng thái đơn. "
                + "Toàn bộ nằm sau hàng rào `/api/admin/**` và **cần vai trò `ADMIN`**.")
public class AdminOrderController {

    /** §A.4: trang đánh số từ 1. */
    private static final String DEFAULT_PAGE = "1";

    /** §A.4: mặc định 12 đơn mỗi trang, khớp {@code ORDERS_PER_PAGE} của frontend. */
    private static final String DEFAULT_LIMIT = "12";

    private static final String MESSAGE_ORDER_NOT_FOUND = "Không tìm thấy đơn hàng với mã này.";

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Tên security scheme khai ở {@code OpenApiConfig} — nút *Authorize* của Swagger UI. */
    private static final String SECURITY_SCHEME = "bearerAuth";

    /** Mô tả 401 dùng chung cho cả ba endpoint — cùng một hàng rào, cùng một câu trả lời. */
    private static final String DESC_UNAUTHORIZED =
            "Không kèm access token, hoặc token sai chữ ký / đã hết hạn; `detail` viết tiếng Việt.";

    /** Mô tả 403 dùng chung cho cả ba endpoint — cùng một hàng rào, cùng một câu trả lời. */
    private static final String DESC_FORBIDDEN =
            "Đã đăng nhập nhưng tài khoản không có vai trò `ADMIN`; `detail` viết tiếng Việt. "
                    + "**Là `403`, không phải `401`** — `401` sẽ khiến client hiểu nhầm là token hết "
                    + "hạn rồi tự đăng xuất người dùng.";

    private final OrderAppService orderAppService;

    /**
     * @param q từ khoá, khớp mã đơn hoặc tên người nhận (bỏ dấu) hoặc SĐT người nhận
     * @param status trạng thái trên dây; rỗng là không lọc
     * @param userId chủ đơn; rỗng là không lọc
     * @param page trang, đánh số từ 1
     * @param limit số đơn mỗi trang
     * @return {@code Paginated<Order>} theo §A.4
     */
    @Operation(summary = "Danh sách đơn hàng cho bảng quản trị",
            description = """
                    Trả trang đơn hàng của **mọi người dùng** theo dạng phân trang chung của hệ \
                    (`items`, `total`, `page`, `limit`, `totalPages`). `total` là số dòng khớp \
                    **bộ lọc**, không phải tổng số đơn.

                    Đây là **song sinh** của `GET /api/orders/me`. Khác biệt duy nhất — và là lý do \
                    namespace này tồn tại — là `userId` ở đây là một **bộ lọc** hợp lệ; ở endpoint \
                    kia chủ đơn luôn đến từ claim `sub` của token.

                    **`q` khớp ba thứ, lấy từ thông tin giao hàng của chính đơn**: mã đơn, **tên \
                    người nhận** (so khớp **bỏ dấu**, kể cả chữ `đ`: `do thi hoa` khớp `Đỗ Thị \
                    Hoa`), và **số điện thoại người nhận** (khớp cả đoạn giữa). Cố ý không tra hồ \
                    sơ tài khoản: đơn của khách vãng lai không có tài khoản nào để tra, và người \
                    đặt hộ vẫn phải tìm ra đơn theo tên người nhận thật.

                    **Không có `sort`.** Thứ tự cố định là ngày đặt **giảm dần** — đơn mới là đơn \
                    cần xử lý. Một `status` không nằm trong năm giá trị hợp lệ cho ra **danh sách \
                    rỗng**, không phải "bỏ qua bộ lọc".""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Trang đơn hàng; danh sách rỗng khi không có dòng nào khớp")
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public PaginatedResponse<OrderResponse> getAdminOrders(
            @Parameter(description = "Từ khoá; khớp **mã đơn**, **tên người nhận đã bỏ dấu**, "
                    + "hoặc **SĐT người nhận**.", example = "do thi hoa")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "`pending` | `confirmed` | `shipping` | `delivered` | `cancelled`.",
                    example = "pending")
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Lọc theo chủ đơn. **Chỉ hợp lệ trong namespace `/admin`** (§C.4.3b).",
                    example = "1")
            @RequestParam(name = "userId", required = false) Long userId,
            @Parameter(description = "Trang cần lấy, **đánh số từ 1**. Mặc định `1`.", example = "1")
            @RequestParam(name = "page", defaultValue = DEFAULT_PAGE) int page,
            @Parameter(description = "Số đơn mỗi trang. Mặc định `12`.", example = "12")
            @RequestParam(name = "limit", defaultValue = DEFAULT_LIMIT) int limit) {
        log.info("AdminOrderController:->getAdminOrders | q={} status={} userId={} page={} limit={}",
                q, status, userId, page, limit);
        return orderAppService.findAdminOrders(
                OrderControllerMapper.toFilter(q, status, userId, page, limit));
    }

    /**
     * @param code mã đơn dạng {@code NSS-20260817-0001}
     * @return đơn hàng
     * @throws OrderNotFoundException khi không có đơn nào mang mã đó
     */
    @Operation(summary = "Chi tiết một đơn hàng theo mã",
            description = """
                    Tra đơn bằng **mã đơn** (`NSS-20260817-0001`), không bằng `id` — khớp URL \
                    `/quan-tri/don-hang/:code` và khớp `GET /api/orders/{code}` sẵn có.

                    Trả về **cùng một hình dạng `Order`** mà ba endpoint đơn hàng kia trả: một đơn \
                    là một đơn, và ba hình dạng cho cùng một thứ sẽ buộc frontend viết ba nhánh \
                    hiển thị.

                    So khớp mã **chính xác từng ký tự** — mã do backend sinh chứ không do người \
                    dùng gõ tự do.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Đơn hàng khớp mã")
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Không có đơn nào mang mã đó; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{code}")
    public OrderResponse getAdminOrder(
            @Parameter(description = "Mã đơn hiển thị cho khách.", example = "NSS-20260825-0001")
            @PathVariable("code") String code) {
        log.info("AdminOrderController:->getAdminOrder | code={}", code);
        OrderResponse order = orderAppService.findOrderByCode(code);
        if (order == null) {
            throw new OrderNotFoundException(MESSAGE_ORDER_NOT_FOUND);
        }
        return order;
    }

    /**
     * @param code mã đơn
     * @param request body đã qua validate
     * @param jwt access token đã được filter chain giải mã — <b>không bao giờ {@code null}</b> ở
     *            đây, vì hàng rào {@code /api/admin/**} đã đòi vai trò {@code ADMIN}
     * @return đơn sau khi chuyển trạng thái
     */
    @Operation(summary = "Đổi trạng thái một đơn hàng",
            description = """
                    Chuyển đơn sang một trạng thái khác và trả về **đơn sau khi chuyển**.

                    **Backend cưỡng chế luồng trạng thái — ô chọn ở giao diện chỉ là tiện tay, \
                    không phải hàng rào:**

                    | Từ | Được chuyển sang |
                    |---|---|
                    | `pending` | `confirmed`, `cancelled` |
                    | `confirmed` | `shipping`, `cancelled` |
                    | `shipping` | `delivered`, `cancelled` |
                    | `delivered` | — (trạng thái cuối) |
                    | `cancelled` | — (trạng thái cuối) |

                    Chuyển ngoài bảng trên trả **`422`**. **Kể cả `status` trùng đúng trạng thái \
                    hiện tại cũng là `422`** — nó không nằm trong danh sách được phép, nên nó là \
                    một lỗi chứ không phải một thao tác không làm gì. `delivered` và `cancelled` \
                    **không quay lui được**: đã giao rồi thì không "chưa xác nhận" lại được, đã huỷ \
                    rồi thì phải tạo đơn mới.

                    Một `status` không nằm trong năm chuỗi hợp lệ cũng trả **`422`**, không phải \
                    `500`.

                    **Mỗi lần chuyển ghi một dòng `order_status_history`** — trạng thái mới và dòng \
                    nhật ký nằm trong **cùng một giao dịch**. `changed_by` ghi định danh admin lấy \
                    từ claim `sub` của token.

                    **Chỉ đổi được trạng thái.** Không có đường sửa items hoặc tiền của đơn, và \
                    cũng không có đường xoá đơn — đơn đã đặt là chứng từ, tiền trên đơn là bản chụp \
                    tại thời điểm đặt.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Đơn hàng sau khi chuyển trạng thái")
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Không có đơn nào mang mã đó; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Chuyển trạng thái không hợp lệ (kể cả chuyển sang chính trạng thái hiện tại), \
                    hoặc `status` không phải một trong năm chuỗi hợp lệ; `detail` viết tiếng Việt \
                    và nêu đích danh cả hai đầu. Body thiếu `status` thì cũng là `422` nhưng **kèm \
                    map `errors`** — đó là lỗi validate theo trường.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping("/{code}/status")
    public OrderResponse updateOrderStatus(
            @Parameter(description = "Mã đơn hiển thị cho khách.", example = "NSS-20260825-0001")
            @PathVariable("code") String code,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("AdminOrderController:->updateOrderStatus | code={} status={}", code, request.getStatus());
        return extractOrThrow(orderAppService.changeOrderStatus(code, request.getStatus(), genChangedBy(jwt)));
    }

    /**
     * Định danh admin ghi vào {@code order_status_history.changed_by}.
     * <p>
     * <b>Lấy từ claim {@code sub}, cùng nguồn với {@code userId} của {@code POST /orders}</b> —
     * cột đó tồn tại để trả lời "ai đổi", và câu trả lời phải đến từ token chứ không từ body. Bảng
     * lưu chuỗi trần chứ không khoá ngoại, vì tác nhân không phải lúc nào cũng là một tài khoản
     * (đơn vãng lai lúc tạo ghi hằng {@code guest}).
     * <p>
     * Null-guard cho một trường hợp không xảy ra được sau hàng rào {@code /api/admin/**}: rẻ, và nó
     * giữ method này đúng nếu ai đó về sau chuyển endpoint sang một tiền tố khác.
     *
     * @param jwt access token đã giải mã
     * @return chuỗi định danh, hoặc {@code null} khi không có token
     */
    private String genChangedBy(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }

    /**
     * Dịch kết quả của tầng application thành payload hoặc exception.
     * <p>
     * Đây là chỗ duy nhất mã lỗi nghiệp vụ gặp mã HTTP: application không được biết HTTP, và kiểu
     * {@code *Exception} sống ở module controller (§3) nên application cũng không ném được chúng.
     * Cùng khuôn với {@code AdminProductController.extractOrThrow}.
     *
     * @param result kết quả của lệnh ghi
     * @return đơn hàng khi thành công
     */
    private OrderResponse extractOrThrow(OrderMutationResponse result) {
        if (result.getOrder() != null) {
            return result.getOrder();
        }
        if (OrderMutationResponse.CODE_ORDER_NOT_FOUND.equals(result.getCode())) {
            throw new OrderNotFoundException(result.getMessage());
        }
        // Moi ca con lai cua endpoint nay la 422: chuoi trang thai la, hoac chuyen ngoai bang.
        // Khong co nhanh `default` nao khac vi changeOrderStatus chi sinh ra dung hai ma loi.
        throw new InvalidOrderDataException(result.getMessage());
    }
}
