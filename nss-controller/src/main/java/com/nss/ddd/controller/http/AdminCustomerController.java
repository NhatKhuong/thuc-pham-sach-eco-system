package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.AdminUserResponse;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.service.customer.CustomerAppService;
import com.nss.ddd.controller.exception.UserNotFoundException;
import com.nss.ddd.controller.mapper.CustomerControllerMapper;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biên REST của khách hàng <b>trong khu quản trị</b> — API_CONTRACT §B.12.3, hai endpoint.
 * <p>
 * <b>Tách khỏi {@link AuthController} vì hai bên phục vụ hai phạm vi mà §C.4 cố ý tách bạch:</b>
 * {@code /auth/**} là namespace của <i>chính người đang đăng nhập</i> — họ đọc và sửa hồ sơ của
 * chính họ; {@code /admin/customers} là namespace <i>đọc chéo mọi người dùng</i>. Cùng lý do
 * frontend đặt {@code adminUsers.api.ts} riêng khỏi {@code auth.api.ts}.
 * <p>
 * <b>CHỈ ĐỌC. Sự vắng mặt của mọi động từ ghi trong file này là contract, không phải chỗ còn
 * thiếu</b> (§B.12.3): không sửa hồ sơ, không xoá, không khoá tài khoản, <b>không đổi vai trò</b>.
 * Riêng vai trò không phải "chưa làm" mà là không được làm ở đây — vai trò chỉ được gán ở phía
 * server (ADR 0002), và một {@code PATCH /admin/customers/{id}/role} là mở đúng cái cửa ADR đó đóng
 * lại.
 * <p>
 * <b>Cũng KHÔNG có {@code GET /admin/customers/{id}/orders}</b> (§B.12.3). Màn hồ sơ gọi lại
 * {@code GET /admin/orders?userId={id}} — chính chỗ §C.4.3b được dùng đến. Thêm một đường thứ hai
 * làm đúng việc đường thứ nhất đã làm là thêm một hàng rào quyền phải nhớ gác lại lần nữa.
 * <p>
 * <b>Trả {@code AdminUserResponse} sáu trường, KHÔNG phải {@code UserResponse}.</b> Cái sau bị khoá
 * cứng ở năm trường cho {@code /auth/**} và javadoc của nó cấm mở rộng; lý do đầy đủ nằm ở javadoc
 * {@code AdminUserResponse}. <b>Không bao giờ kèm {@code password}</b>, kể cả hash.
 * <p>
 * <b>Không có {@code @PreAuthorize}</b> — hàng rào là một filter trên cả tiền tố
 * {@code /api/admin/**} (§C.4.3a). Xem javadoc {@link AdminOrderController}.
 * <p>
 * Mọi {@code @RequestParam} / {@code @PathVariable} đều <b>khai tên tường minh</b> vì dự án không
 * bật cờ {@code -parameters}.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@Tag(name = "Quản trị khách hàng",
        description = "Tra cứu tài khoản người dùng ở khu quản trị. **Chỉ đọc.** "
                + "Toàn bộ nằm sau hàng rào `/api/admin/**` và **cần vai trò `ADMIN`**.")
public class AdminCustomerController {

    /** §A.4: trang đánh số từ 1. */
    private static final String DEFAULT_PAGE = "1";

    /** §A.4: mặc định 10 khách mỗi trang, khớp {@code USERS_PER_PAGE} của frontend. */
    private static final String DEFAULT_LIMIT = "10";

    private static final String MESSAGE_USER_NOT_FOUND = "Không tìm thấy tài khoản này.";

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Tên security scheme khai ở {@code OpenApiConfig} — nút *Authorize* của Swagger UI. */
    private static final String SECURITY_SCHEME = "bearerAuth";

    /** Mô tả 401 dùng chung cho cả hai endpoint. */
    private static final String DESC_UNAUTHORIZED =
            "Không kèm access token, hoặc token sai chữ ký / đã hết hạn; `detail` viết tiếng Việt.";

    /** Mô tả 403 dùng chung cho cả hai endpoint. */
    private static final String DESC_FORBIDDEN =
            "Đã đăng nhập nhưng tài khoản không có vai trò `ADMIN`; `detail` viết tiếng Việt. "
                    + "**Là `403`, không phải `401`** — `401` sẽ khiến client hiểu nhầm là token hết "
                    + "hạn rồi tự đăng xuất người dùng.";

    private final CustomerAppService customerAppService;

    /**
     * @param q từ khoá, khớp họ tên (bỏ dấu) hoặc email hoặc SĐT
     * @param role vai trò cần lọc; <b>bỏ trống là {@code customer}</b>
     * @param page trang, đánh số từ 1
     * @param limit số khách mỗi trang
     * @return {@code Paginated<User>} theo §A.4
     */
    @Operation(summary = "Danh sách khách hàng cho bảng quản trị",
            description = """
                    Trả trang tài khoản theo dạng phân trang chung của hệ (`items`, `total`, \
                    `page`, `limit`, `totalPages`).

                    **`role` bỏ trống nghĩa là `customer`, không phải "mọi vai trò".** Tài khoản \
                    quản trị là nhân viên nội bộ, không phải khách — nên gọi endpoint này không kèm \
                    tham số nào thì **không có dòng `admin` nào** trong kết quả. Đây là **mặc \
                    định, không phải hàng rào**: truyền `role=admin` vẫn trả về tài khoản quản trị. \
                    Quyền vào được namespace này đã do filter `/api/admin/**` gác.

                    Đây phải là **đúng tập** mà `customerCount` của `GET /api/admin/stats/overview` \
                    đếm — hai chỗ này là hai chỗ duy nhất trong hệ đếm người dùng.

                    **`q` khớp ba thứ**: họ tên (so khớp **bỏ dấu**, kể cả chữ `đ`), email (so \
                    khớp trên chuỗi đã hạ chữ thường), và số điện thoại (**khớp cả đoạn giữa** — \
                    người ta hay gõ `345678`).

                    **Không có `sort`.** Thứ tự cố định là **`id` tăng dần**, không phải "mới nhất \
                    trước": `User` không có `createdAt` nên không tồn tại mốc thời gian nào để xếp \
                    theo.

                    **Không bao giờ kèm `password`**, kể cả dạng đã băm.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Trang khách hàng; danh sách rỗng khi không có dòng nào khớp")
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public PaginatedResponse<AdminUserResponse> getAdminCustomers(
            @Parameter(description = "Từ khoá; khớp **họ tên đã bỏ dấu**, **email**, hoặc **SĐT**.",
                    example = "do thi hoa")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "`customer` | `admin`. **Bỏ trống nghĩa là `customer`.**",
                    example = "customer")
            @RequestParam(name = "role", required = false) String role,
            @Parameter(description = "Trang cần lấy, **đánh số từ 1**. Mặc định `1`.", example = "1")
            @RequestParam(name = "page", defaultValue = DEFAULT_PAGE) int page,
            @Parameter(description = "Số khách mỗi trang. Mặc định `10`.", example = "10")
            @RequestParam(name = "limit", defaultValue = DEFAULT_LIMIT) int limit) {
        log.info("AdminCustomerController:->getAdminCustomers | q={} role={} page={} limit={}",
                q, role, page, limit);
        return customerAppService.findAdminUsers(
                CustomerControllerMapper.toFilter(q, role, page, limit));
    }

    /**
     * @param id khoá chính của tài khoản
     * @return khách hàng
     * @throws UserNotFoundException khi id không khớp dòng nào
     */
    @Operation(summary = "Chi tiết một khách hàng theo id",
            description = """
                    Tra tài khoản bằng **`id`** (khoá chính), **không** bằng email — email là thứ \
                    khách tự sửa được ở `/tai-khoan`, mà link hồ sơ đã lưu không được hỏng sau lần \
                    Lưu đầu tiên. Cùng lý do `GET /api/admin/products/{id}` khoá theo `id` chứ \
                    không theo `slug`.

                    **Không lọc theo vai trò.** Một `id` có thật thì tra ra được, kể cả khi đó là \
                    tài khoản quản trị — nếu không thì một dòng vừa hiện trong bảng \
                    `?role=admin` sẽ trả `404` khi bấm vào.

                    **Không bao giờ kèm `password`**, kể cả dạng đã băm.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Tài khoản khớp id")
    @ApiResponse(responseCode = "401", description = DESC_UNAUTHORIZED,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = DESC_FORBIDDEN,
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Id không khớp tài khoản nào; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public AdminUserResponse getAdminCustomer(
            @Parameter(description = "Khoá chính của tài khoản.", example = "1")
            @PathVariable("id") Long id) {
        log.info("AdminCustomerController:->getAdminCustomer | userId={}", id);
        AdminUserResponse user = customerAppService.findAdminUserById(id);
        if (user == null) {
            throw new UserNotFoundException(MESSAGE_USER_NOT_FOUND);
        }
        return user;
    }
}
