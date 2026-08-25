package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.AdminOverviewResponse;
import com.nss.ddd.application.service.stats.StatsAppService;
import com.nss.ddd.controller.exception.InvalidDateRangeException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biên REST của <b>số liệu tổng quan</b> — API_CONTRACT §B.12.4, một endpoint.
 * <p>
 * <b>Đây là endpoint chỉ đọc và sẽ luôn chỉ đọc</b> (§B.12.4): mọi con số ở đây được <i>suy ra</i>
 * từ đơn hàng, sản phẩm và tài khoản, không phải một bản ghi ai đó sửa được. Một động từ ghi trên
 * {@code /api/admin/stats/**} là dấu hiệu số liệu đang được nhập tay ở đâu đó thay vì tính ra.
 * <p>
 * <b>Số liệu do backend gộp, không do client</b> — cùng lý do với §C.3: gộp ở trình duyệt nghĩa là
 * tải toàn bộ đơn hàng của mọi khách về máy người dùng.
 * <p>
 * <b>Không có {@code @PreAuthorize}</b> — hàng rào là một filter trên cả tiền tố
 * {@code /api/admin/**} (§C.4.3a). Xem javadoc {@link AdminOrderController}.
 * <p>
 * <b>Phép kiểm dải của {@code days} nằm ở ĐÂY chứ không ở tầng application</b>: kết quả của nó là
 * một mã HTTP (400), và mã HTTP là khái niệm của tầng này. Tầng application nhận một con số đã
 * hợp lệ.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@Tag(name = "Quản trị tổng quan",
        description = "Số liệu tổng hợp cho màn Tổng quan. **Chỉ đọc.** "
                + "Nằm sau hàng rào `/api/admin/**` và **cần vai trò `ADMIN`**.")
public class AdminStatsController {

    /**
     * Khoảng mặc định khi client không gửi {@code days} (§B.12.4 — "bỏ trống ⇒ 30").
     * <p>
     * Là chuỗi vì {@code @RequestParam(defaultValue = ...)} chỉ nhận chuỗi hằng.
     */
    private static final String DEFAULT_DAYS = "30";

    /**
     * Biên dưới của {@code days} — <b>một giả định đã nêu rõ, không phải một con số trong hợp
     * đồng.</b>
     * <p>
     * §B.12.4 nói "{@code days} ngoài dải hợp lý → 400" mà không pin con số nào; giao diện hiện chỉ
     * có hai nút 7 và 30. Backlog 0019 chốt dải {@code 1..365} và ghi ra để Owner phủ quyết được.
     * <p>
     * {@code 1} chứ không {@code 7}: "hôm nay" là một câu hỏi hợp lệ, và chặn nó ở đây sẽ là một
     * giới hạn do backend tự nghĩ ra.
     */
    private static final int MIN_DAYS = 1;

    /**
     * Biên trên của {@code days} — xem {@link #MIN_DAYS}.
     * <p>
     * {@code 365} là một năm; {@code revenueByDay} zero-fill đúng {@code days} phần tử nên con số
     * này cũng là trần độ dài của mảng trả về. Nới nó lên là nới luôn kích thước payload.
     */
    private static final int MAX_DAYS = 365;

    private static final String MESSAGE_INVALID_DAYS =
            "Khoảng thời gian phải nằm trong khoảng từ " + MIN_DAYS + " đến " + MAX_DAYS + " ngày.";

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Tên security scheme khai ở {@code OpenApiConfig} — nút *Authorize* của Swagger UI. */
    private static final String SECURITY_SCHEME = "bearerAuth";

    private final StatsAppService statsAppService;

    /**
     * @param days số ngày của khoảng; bỏ trống là 30
     * @return số liệu tổng quan
     * @throws InvalidDateRangeException khi {@code days} nằm ngoài dải {@code 1..365}
     */
    @Operation(summary = "Số liệu tổng quan cho màn Tổng quan",
            description = """
                    Trả sáu con số của `AdminOverview`, tính trong `days` ngày gần nhất **theo múi \
                    giờ cửa hàng** (`Asia/Ho_Chi_Minh`) — một đơn đặt lúc 20:00 giờ Việt Nam rơi \
                    vào **đúng ngày đó**, không bị đẩy sang hôm trước.

                    **Ba định nghĩa được ghim ở đây, vì chúng là phần dễ tranh cãi nhất:**
                    - **`revenue`** = tổng `total` của mọi đơn **không** ở trạng thái `cancelled`. \
                    Đơn đã huỷ **vẫn** vào `orderCount` và vào cột `cancelled` — nó đã xảy ra — \
                    nhưng không phải tiền cửa hàng thu được.
                    - **`customerCount`** chỉ đếm `role == "customer"`, và là **đúng tập** mà \
                    `GET /api/admin/customers` trả về khi không kèm tham số.
                    - **`lowStockCount`** = số sản phẩm có `0 < stock <= 10`, **đúng con số** mà \
                    bộ lọc `stockStatus=low_stock` dùng.

                    **Cái gì theo khoảng, cái gì không:** `revenue`, `orderCount`, `revenueByDay` \
                    và `ordersByStatus` nằm trong **cùng một** khoảng `days`. `customerCount` và \
                    `lowStockCount` là **ảnh chụp hiện tại** — `User` không có chiều thời gian, tồn \
                    kho chỉ có giá trị "ngay lúc này".

                    **Chuỗi thời gian DÀY, zero-filled bởi backend:** `revenueByDay` có **đúng \
                    `days` phần tử**, tăng dần, `date` dạng `YYYY-MM-DD`, ngày không có đơn trả \
                    `revenue: 0`. `ordersByStatus` có **đủ cả 5 trạng thái**, kể cả `count: 0`.

                    **Hai bất biến kiểm được thẳng từ response:** \
                    `revenue == sum(revenueByDay[].revenue)` và \
                    `orderCount == sum(ordersByStatus[].count)`.

                    **`days` là preset, không phải khoảng tuỳ ý.** Ngoài dải `1..365` trả **`400`**; \
                    backend **không** âm thầm kẹp giá trị — một khoảng khác thứ người dùng yêu cầu \
                    là một câu trả lời sai im lặng.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Số liệu tổng quan của khoảng đã yêu cầu")
    @ApiResponse(responseCode = "400",
            description = "`days` nằm ngoài dải `1..365`; `detail` viết tiếng Việt. "
                    + "**Không** kèm map `errors` — đây là lỗi tham số, không phải lỗi theo trường.",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401",
            description = "Không kèm access token, hoặc token sai chữ ký / đã hết hạn; "
                    + "`detail` viết tiếng Việt.",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403",
            description = "Đã đăng nhập nhưng tài khoản không có vai trò `ADMIN`; "
                    + "`detail` viết tiếng Việt. **Là `403`, không phải `401`.**",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/overview")
    public AdminOverviewResponse getOverview(
            @Parameter(description = "Số ngày của khoảng, `1..365`. Mặc định `30`.", example = "30")
            @RequestParam(name = "days", defaultValue = DEFAULT_DAYS) int days) {
        log.info("AdminStatsController:->getOverview | days={}", days);
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new InvalidDateRangeException(MESSAGE_INVALID_DAYS);
        }
        return statsAppService.findOverview(days);
    }
}
