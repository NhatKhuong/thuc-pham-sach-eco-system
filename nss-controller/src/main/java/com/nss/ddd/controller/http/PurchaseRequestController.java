package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.PurchaseRequestResponse;
import com.nss.ddd.application.service.purchaserequest.PurchaseRequestAppService;
import com.nss.ddd.controller.dto.CreateOrderRequest;
import com.nss.ddd.controller.exception.MissingIdempotencyKeyException;
import com.nss.ddd.controller.exception.PurchaseRequestNotFoundException;
import com.nss.ddd.controller.mapper.OrderControllerMapper;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biên REST của luồng mua hàng bất đồng bộ — Luồng B (backlog 0039 §Contract).
 * <p>
 * Trả DTO trần, <b>không bọc {@code ResultMessage}</b> (ADR 0001, giống {@code OrderController}).
 * <p>
 * <b>Tách khỏi {@code OrderController} dù cùng namespace {@code /api/orders}</b> — cùng lý do đã
 * viết ở {@code AdminOrderController} vs {@code OrderController}: hai aggregate khác nhau
 * ({@code customer_order} vs {@code purchase_request}) thì service khác nhau, và để chung một
 * controller sẽ mời một lời gọi nhầm aggregate lọt vào.
 * <p>
 * <b>Cả hai endpoint đều công khai</b> (§Contract): {@code POST /orders/async} công khai vì nó y hệt
 * {@code POST /orders} — khách vãng lai đặt hàng được, {@code userId} vẫn chỉ đến từ claim
 * {@code sub} (§C.2), không bao giờ từ body. {@code GET /orders/requests/{requestId}} công khai vì
 * {@code requestId} chính là token — sinh ngẫu nhiên an toàn mật mã (16 hex), không đoán được, cùng
 * mô hình bảo mật với {@code GET /orders/{code}}.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Đơn hàng bất đồng bộ",
        description = "Luồng async qua Kafka: submit trả 202 ngay, client polling trạng thái sau (backlog 0039).")
public class PurchaseRequestController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    private static final String MESSAGE_MISSING_IDEMPOTENCY_KEY =
            "Thiếu header Idempotency-Key, vui lòng thử lại.";

    private static final String MESSAGE_PURCHASE_REQUEST_NOT_FOUND =
            "Không tìm thấy yêu cầu mua hàng với requestId này.";

    private final PurchaseRequestAppService purchaseRequestAppService;

    /**
     * @param request        body giống hệt {@code POST /orders} — {@code CreateOrderRequest}
     * @param idempotencyKey header {@code Idempotency-Key}, bắt buộc
     * @param jwt            access token đã giải mã; {@code null} khi khách chưa đăng nhập (hợp lệ)
     * @return {@code requestId} + {@code status = "PENDING"} (hoặc trạng thái hiện tại nếu đây là
     *         một lần retry đúng {@code Idempotency-Key} đã submit trước đó)
     * @throws MissingIdempotencyKeyException khi thiếu hoặc rỗng header {@code Idempotency-Key}
     */
    @Operation(summary = "Nộp yêu cầu mua hàng (async)",
            description = """
                    Trả về **`202`** NGAY — không chạm Redis/MySQL stock ở bước này. Việc tạo đơn \
                    thật chạy bất đồng bộ qua Kafka; client polling kết quả bằng \
                    `GET /orders/requests/{requestId}`.

                    **Body y hệt `POST /orders`.** Header `Idempotency-Key` bắt buộc — trùng \
                    header ở một lần gọi lại (double-click, timeout+retry) trả về ĐÚNG \
                    `requestId`/`status` của lần đầu, không tạo thêm gì.""")
    @ApiResponse(responseCode = "202", description = "Đã nhận yêu cầu, đang xử lý bất đồng bộ")
    @ApiResponse(responseCode = "400",
            description = "Thiếu header Idempotency-Key; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = "Body sai quy tắc nghiệp vụ — cùng bảng lỗi với `POST /orders`",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/orders/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PurchaseRequestResponse submitAsync(@Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("PurchaseRequestController:->submitAsync | missing Idempotency-Key");
            throw new MissingIdempotencyKeyException(MESSAGE_MISSING_IDEMPOTENCY_KEY);
        }
        Long userId = extractUserId(jwt);
        log.info("PurchaseRequestController:->submitAsync | userId={} itemCount={} idempotencyKey={}",
                userId, request.getItems().size(), idempotencyKey);
        return purchaseRequestAppService.submitAsync(
                OrderControllerMapper.toCommand(request, userId), idempotencyKey);
    }

    /**
     * @param requestId khoá tự nhiên, dạng {@code PR-<16 hex>}
     * @return trạng thái hiện tại của yêu cầu
     * @throws PurchaseRequestNotFoundException khi không có yêu cầu nào mang id này
     */
    @Operation(summary = "Tra trạng thái yêu cầu mua hàng (polling)",
            description = """
                    `status` là một trong `PENDING` / `SUCCESS` / `FAILED`. `orderCode` chỉ có giá \
                    trị khi `SUCCESS`; `failureCode`/`failureMessage` chỉ có giá trị khi `FAILED`.

                    **Công khai có chủ ý** — `requestId` là token, sinh ngẫu nhiên an toàn mật mã, \
                    không đoán được (cùng mô hình với `GET /orders/{code}`).""")
    @ApiResponse(responseCode = "200", description = "Trạng thái hiện tại của yêu cầu")
    @ApiResponse(responseCode = "404",
            description = "Không có yêu cầu nào mang requestId này; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/orders/requests/{requestId}")
    public PurchaseRequestResponse getStatus(
            @Parameter(description = "requestId, ví dụ `PR-1a2b3c4d5e6f7a8b`.", example = "PR-1a2b3c4d5e6f7a8b")
            @PathVariable(name = "requestId") String requestId) {
        log.info("PurchaseRequestController:->getStatus | requestId={}", requestId);
        PurchaseRequestResponse response = purchaseRequestAppService.findByRequestId(requestId);
        if (response == null) {
            throw new PurchaseRequestNotFoundException(MESSAGE_PURCHASE_REQUEST_NOT_FOUND);
        }
        return response;
    }

    /**
     * @param jwt access token đã được filter chain giải mã; {@code null} khi khách chưa đăng nhập
     * @return id người dùng, hoặc {@code null} cho khách vãng lai — cùng khuôn
     *         {@code OrderController#extractUserId}
     */
    private Long extractUserId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }
}
