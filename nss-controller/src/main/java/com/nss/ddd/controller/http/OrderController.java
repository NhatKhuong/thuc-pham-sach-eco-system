package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.CouponValidationResponse;
import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.model.response.OrderResponse;
import com.nss.ddd.application.service.order.OrderAppService;
import com.nss.ddd.controller.dto.CreateOrderRequest;
import com.nss.ddd.controller.exception.CouponNotApplicableException;
import com.nss.ddd.controller.exception.EmptyOrderException;
import com.nss.ddd.controller.exception.InvalidOrderDataException;
import com.nss.ddd.controller.exception.OrderNotFoundException;
import com.nss.ddd.controller.exception.OutOfStockException;
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
 * Biên REST của đơn hàng — API_CONTRACT §B.6, ba endpoint của backlog 0014 phase 3.
 * <p>
 * Trả DTO trần, <b>không bọc {@code ResultMessage}</b> (ADR 0001, giống {@code ProductController});
 * thất bại dùng mã HTTP thật và {@code ProblemDetail} do {@code GlobalExceptionHandler} dựng.
 * <p>
 * <b>Hai công khai, một cần token — và sự pha trộn đó là chủ ý, không phải sót.</b>
 * {@code POST /orders} công khai vì khách vãng lai phải mua được hàng trước khi đăng ký tài khoản;
 * {@code GET /orders/{code}} công khai vì đó là <i>lối duy nhất</i> để chính khách vãng lai ấy xem
 * lại đơn của mình. {@code GET /orders/me} thì bắt buộc có token, vì nó lọc theo chủ đơn.
 * <p>
 * <b>Khách vãng lai và token hỏng là hai chuyện khác nhau trên đường công khai.</b>
 * {@code POST /orders} vẫn đi qua bộ lọc bearer token của {@code SecurityConfig}: không gửi header
 * nào thì {@code @AuthenticationPrincipal Jwt jwt} là {@code null} và đơn ra đời với
 * {@code userId = null}; gửi một token sai chữ ký hoặc hết hạn thì request bị chặn <b>401</b>
 * <i>trước khi</i> vào tới method này — {@code permitAll} mở đường cho người <i>không có</i> token,
 * không phải cho token sai.
 * <p>
 * <b>{@code /orders/me} không nhận {@code userId} dưới bất kỳ dạng nào</b> — không query, không
 * path, không body (§C.4.1). Chủ đơn chỉ đến từ claim {@code sub}. Việc liệt kê chéo người dùng
 * thuộc {@code GET /admin/orders} và phải là một endpoint song sinh riêng (§C.4.3b); thêm một
 * {@code ?userId=} vào đây là <b>rò rỉ dữ liệu</b>, không phải một tiện ích.
 * <p>
 * Mọi {@code @RequestParam} / {@code @PathVariable} phải <b>khai tên tường minh</b> vì dự án không
 * bật cờ {@code -parameters} (xem javadoc {@code ProductController}) — bỏ tên đi thì tài liệu hiện
 * {@code arg0} mà vẫn trả 200.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Đơn hàng",
        description = "Đặt hàng và tra cứu đơn. Đặt hàng và tra theo mã là công khai; "
                + "danh sách đơn của tôi cần token.")
public class OrderController {

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Tên security scheme khai ở {@code OpenApiConfig} — nút *Authorize* của Swagger UI. */
    private static final String SECURITY_SCHEME = "bearerAuth";

    private static final String MESSAGE_ORDER_NOT_FOUND =
            "Không tìm thấy đơn hàng với mã này.";

    private final OrderAppService orderAppService;

    /**
     * @param request body đã qua validate
     * @param jwt access token đã được filter chain giải mã; <b>{@code null} khi khách chưa đăng
     *            nhập</b> — đó là ca hợp lệ, không phải lỗi
     * @return đơn hàng vừa tạo, kèm mọi con số tiền do backend tự tính
     * @throws EmptyOrderException khi giỏ không có dòng nào
     * @throws OutOfStockException khi một dòng không còn đủ hàng
     * @throws CouponNotApplicableException khi mã giảm giá không dùng được cho đơn này
     * @throws InvalidOrderDataException khi dữ liệu đơn sai quy tắc nghiệp vụ
     */
    @Operation(summary = "Đặt hàng",
            description = """
                    Tạo một đơn hàng mới ở trạng thái `pending` và trả về **`201`** kèm đơn vừa tạo.

                    **Công khai — khách vãng lai đặt hàng được.** Có token thì backend gán `userId` \
                    từ claim `sub`; không có token thì `userId` là `null`. **Client không bao giờ \
                    gửi `userId`** — payload không có trường đó, và đó là chủ ý (§C.2).

                    **Backend bỏ qua mọi con số tiền client gửi lên** (§C.1). `price`, \
                    `originalPrice`, `stock`, `slug`, `image`, `unit` trong mỗi `item` chỉ là bản \
                    chụp của giỏ hàng; giá thực tế được tra lại từ database theo `productId`. Thứ \
                    tự tính là cố định:

                    1. `subtotal` = tổng (giá tra lại × số lượng)
                    2. `discount` = từ mã giảm giá, tính trên `subtotal` vừa có
                    3. `shippingFee` = tính trên **`subtotal − discount`**, miễn phí từ 500.000 ₫, \
                    dưới ngưỡng là 30.000 ₫
                    4. `total` = `subtotal − discount + shippingFee`

                    `paymentMethod` nhận đúng bốn giá trị: `cod`, `bank_transfer`, `momo`, `vnpay` \
                    — hai giá trị sau **chỉ là nhãn phương thức**, chưa có luồng thanh toán nào.

                    **Cả đơn là một giao dịch.** Tồn kho bị trừ ngay khi đặt và lượt dùng của mã \
                    giảm giá tăng trong cùng giao dịch đó; hỏng ở bất kỳ bước nào thì không còn dấu \
                    vết nào — không đơn mồ côi, không kho bị trừ oan, không lượt mã bị đốt.

                    **Dòng hàng trả về không có trường `stock`** — tồn kho tại thời điểm đặt là con \
                    số vô nghĩa trên một chứng từ.""")
    @ApiResponse(responseCode = "201", description = "Đơn đã được tạo ở trạng thái `pending`")
    @ApiResponse(responseCode = "400",
            description = "Giỏ hàng trống (`items: []`); `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401",
            description = """
                    Có gửi `Authorization` nhưng token sai chữ ký hoặc đã hết hạn.

                    **Endpoint này công khai, nhưng công khai nghĩa là đi được khi \
                    _không có_ token — không phải khi token sai.** Không gửi header nào thì đây là \
                    đơn của khách vãng lai và trả `201`.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = """
                    Một dòng hàng không mua được: không đủ tồn kho, sản phẩm không tồn tại, hoặc \
                    sản phẩm đã bị gỡ khỏi cửa hàng. `detail` viết tiếng Việt và **nêu tên món \
                    hàng**. Đơn đã được huỷ trọn vẹn — tồn kho không bị trừ.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Ba nhóm lý do, cùng trả `422` và `detail` viết tiếng Việt:

                    - **Body không hợp lệ** — thiếu `items` / `shipping` / `paymentMethod`, một \
                    dòng thiếu `productId` / `name` / `quantity` / `price`, hoặc một trường của \
                    `shipping` bỏ trống. Kèm phần mở rộng `errors` map `tên trường → thông điệp`.
                    - **Mã giảm giá không dùng được** — không tồn tại, đã tắt, ngoài cửa sổ hiệu \
                    lực, hết lượt, hoặc đơn chưa đạt giá trị tối thiểu (`detail` nêu rõ con số).
                    - **`paymentMethod` không nằm trong bốn giá trị hợp lệ.**""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        log.info("OrderController:->createOrder | userId={} itemCount={} couponCode={}",
                userId, request.getItems().size(), request.getCouponCode());
        return extractOrThrow(
                orderAppService.createOrder(OrderControllerMapper.toCommand(request, userId)));
    }

    /**
     * @param jwt access token đã được filter chain giải mã; <b>không bao giờ {@code null}</b> ở đây
     *            vì đường dẫn này {@code authenticated()}
     * @return các đơn của chính người đang đăng nhập, mới nhất trước
     */
    @Operation(summary = "Danh sách đơn hàng của tôi",
            description = """
                    Trả về **mọi** đơn của chính người đang đăng nhập, mới nhất trước.

                    **Không phân trang** — hợp đồng trả mảng trần.

                    **Chủ đơn lấy từ claim `sub` của token và chỉ từ đó.** Endpoint này \
                    **không nhận `userId`** qua query, path hay body — không có tham số nào như \
                    vậy, và sẽ không bao giờ có. Admin cần xem đơn của khách khác thì dùng \
                    `GET /admin/orders?userId=` (namespace riêng, gác bằng lớp bảo mật riêng).

                    Đơn của khách vãng lai (`userId: null`) **không bao giờ xuất hiện ở đây** — \
                    chúng chỉ tra được bằng mã đơn qua `GET /api/orders/{code}`.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200",
            description = "Các đơn của người đang đăng nhập; mảng rỗng khi chưa có đơn nào")
    @ApiResponse(responseCode = "401",
            description = "Thiếu access token, token sai chữ ký hoặc đã hết hạn; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/orders/me")
    public List<OrderResponse> getMyOrders(@AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        log.info("OrderController:->getMyOrders | userId={}", userId);
        return orderAppService.findMyOrders(userId);
    }

    /**
     * @param code mã đơn dạng {@code NSS-20260826-K7M2QX9P4T}
     * @return đơn hàng tương ứng
     * @throws OrderNotFoundException khi không có đơn nào mang mã này
     */
    @Operation(summary = "Tra cứu đơn hàng theo mã",
            description = """
                    Trả về một đơn theo **mã đơn** (`NSS-20260826-K7M2QX9P4T`), không phải theo \
                    `id`.

                    **Công khai có chủ ý.** Đây là lối duy nhất để khách vãng lai xem lại đơn của \
                    mình, vì `GET /api/orders/me` lọc nghiêm ngặt theo chủ đơn. Lối này an toàn \
                    được là nhờ **mã đơn khó đoán**: 10 ký tự cuối sinh ngẫu nhiên an toàn mật mã, \
                    không gian `32^10 ≈ 1,13 × 10^15` (ADR 0006).

                    **Đơn tạo trước 2026-08-26 mang mã tuần tự cũ** dạng `NSS-YYYYMMDD-NNNN` và \
                    vẫn tra được bình thường — không backfill, và endpoint này **không** kiểm khuôn \
                    dạng mã.

                    So khớp mã **phân biệt hoa thường** — mã do backend sinh ra và đi thẳng lên \
                    URL, không phải chuỗi người dùng gõ tự do.""")
    @ApiResponse(responseCode = "200", description = "Đơn hàng tương ứng với mã")
    @ApiResponse(responseCode = "404",
            description = "Không có đơn nào mang mã này; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/orders/{code}")
    public OrderResponse getOrderByCode(
            @Parameter(description = "Mã đơn hàng, ví dụ `NSS-20260826-K7M2QX9P4T`.",
                    example = "NSS-20260826-K7M2QX9P4T")
            @PathVariable(name = "code") String code) {
        log.info("OrderController:->getOrderByCode | code={}", code);
        OrderResponse order = orderAppService.findOrderByCode(code);
        if (order == null) {
            throw new OrderNotFoundException(MESSAGE_ORDER_NOT_FOUND);
        }
        return order;
    }

    // ========== HELPERS ==========

    /**
     * Đọc chủ đơn từ claim {@code sub}.
     * <p>
     * <b>{@code jwt == null} là ca hợp lệ trên hai đường công khai</b>, không phải một thiếu sót:
     * nó nghĩa là request không mang token nào, tức khách vãng lai. Một token <i>sai</i> thì không
     * bao giờ tới được đây — filter chain đã trả 401 từ trước.
     * <p>
     * Khuôn {@code Long.valueOf(jwt.getSubject())} lấy đúng từ {@code AuthController.logout}.
     *
     * @param jwt access token đã giải mã, hoặc {@code null} khi không có token
     * @return id người dùng, hoặc {@code null} cho khách vãng lai
     */
    private Long extractUserId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }

    /**
     * Dịch kết quả của tầng application thành payload hoặc exception.
     * <p>
     * Đây là chỗ duy nhất mã lỗi nghiệp vụ gặp mã HTTP: application không được biết HTTP, và kiểu
     * {@code *Exception} sống ở module controller (§3) nên application cũng không ném được chúng.
     * <p>
     * <b>Nhánh cuối rơi về 422</b> chứ không phải 409 hay 400: một mã lỗi mới ra đời mà quên khai ở
     * đây thì "không xử lý được yêu cầu" là câu trả lời an toàn, còn "hết hàng" hay "giỏ trống" là
     * những lời khẳng định <i>sai</i> về dữ liệu. Cùng kỷ luật với
     * {@code CouponController.extractOrThrow}.
     *
     * @param result kết quả tạo đơn
     * @return đơn hàng khi thành công
     */
    private OrderResponse extractOrThrow(OrderMutationResponse result) {
        if (result.getOrder() != null) {
            return result.getOrder();
        }
        if (OrderMutationResponse.CODE_EMPTY_ORDER.equals(result.getCode())) {
            throw new EmptyOrderException(result.getMessage());
        }
        if (OrderMutationResponse.CODE_OUT_OF_STOCK.equals(result.getCode())) {
            throw new OutOfStockException(result.getMessage());
        }
        if (CouponValidationResponse.CODE_COUPON_NOT_APPLICABLE.equals(result.getCode())) {
            // Dung lai dung exception ma POST /coupons/validate nem: mot luat, mot ma HTTP.
            throw new CouponNotApplicableException(result.getMessage());
        }
        throw new InvalidOrderDataException(result.getMessage());
    }
}
