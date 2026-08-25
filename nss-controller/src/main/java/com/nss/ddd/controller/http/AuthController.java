package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.AuthMutationResponse;
import com.nss.ddd.application.model.response.AuthResponse;
import com.nss.ddd.application.model.response.PasswordMutationResponse;
import com.nss.ddd.application.model.response.PasswordResetMutationResponse;
import com.nss.ddd.application.model.response.ProfileMutationResponse;
import com.nss.ddd.application.model.response.UserResponse;
import com.nss.ddd.application.service.auth.AuthAppService;
import com.nss.ddd.controller.config.ForgotPasswordRateLimiter;
import com.nss.ddd.controller.dto.ChangePasswordRequest;
import com.nss.ddd.controller.dto.ForgotPasswordRequest;
import com.nss.ddd.controller.dto.LoginRequest;
import com.nss.ddd.controller.dto.RefreshTokenRequest;
import com.nss.ddd.controller.dto.RegisterRequest;
import com.nss.ddd.controller.dto.ResetPasswordRequest;
import com.nss.ddd.controller.dto.UpdateProfileRequest;
import com.nss.ddd.controller.exception.DuplicateEmailException;
import com.nss.ddd.controller.exception.InvalidCredentialsException;
import com.nss.ddd.controller.exception.InvalidCurrentPasswordException;
import com.nss.ddd.controller.exception.InvalidResetTokenException;
import com.nss.ddd.controller.exception.TooManyRequestsException;
import com.nss.ddd.controller.exception.UserNotFoundException;
import com.nss.ddd.controller.mapper.AuthControllerMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biên REST của vòng phiên xác thực — API_CONTRACT §B.4.
 * <p>
 * Trả DTO trần, <b>không bọc {@code ResultMessage}</b> (ADR 0001); thất bại dùng mã HTTP thật và
 * {@code ProblemDetail} do {@code GlobalExceptionHandler} dựng.
 * <p>
 * <b>Ba endpoint đầu công khai, {@code logout} cần token.</b> Đó không phải lựa chọn của file này
 * mà của {@code SecurityConfig} — nơi duy nhất quyết định endpoint nào công khai. {@code logout}
 * cần token vì nó phải biết <i>ai</i> đang đăng xuất để chỉ thu hồi phiên của chính người đó (§C.2).
 * <p>
 * <b>{@code PUT /auth/me} và {@code PUT /auth/password} đã có mặt từ backlog 0016.</b> Cả hai
 * <b>cần token</b> và được phủ bởi dòng {@code .anyRequest().authenticated()} của
 * {@code SecurityConfig} — không có matcher hẹp nào phía trên nuốt hai đường dẫn này, nên chúng
 * không cần một dòng luật riêng.
 * <p>
 * <b>{@code POST /auth/forgot-password} và {@code POST /auth/reset-password} ra đời ở backlog
 * 0017</b>, sau khi ADR 0004 gỡ hai khoảng trống hạ tầng đã chặn chúng qua hai ticket (không có chỗ
 * lưu token đặt lại, không có đường gửi mail). <b>Cả hai CÔNG KHAI</b>, và đó là ngoại lệ so với
 * backlog 0016 — {@code SecurityConfig.PATHS_AUTH_PUBLIC} phải khai tường minh cả hai, vì
 * {@code .anyRequest().authenticated()} sẽ nuốt chúng.
 * <p>
 * <b>Lỗ hổng thứ ba vẫn còn, và nó nằm ở repo khác:</b> frontend chưa có trang nhận token, nên link
 * trong email trỏ tới một đường dẫn chưa tồn tại. Đó là <i>điều đã biết</i> chứ không phải một bất
 * ngờ lúc ghép — backend đi trước một bước đúng như vòng phiên của 0010 đã đi (backlog 0015 lệch
 * #1). Đường dẫn nằm sau biến môi trường {@code PASSWORD_RESET_URL} nên nó đúng ngay khi trang được
 * dựng.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Xác thực",
        description = "Đăng ký, đăng nhập, gia hạn phiên, đăng xuất, sửa hồ sơ và đặt lại mật khẩu.")
public class AuthController {

    /** Mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Tên security scheme khai ở {@code OpenApiConfig} — nút *Authorize* của Swagger UI. */
    private static final String SECURITY_SCHEME = "bearerAuth";

    /**
     * Tên claim mang id dòng {@code refresh_token} của phiên hiện tại; phải khớp hằng cùng tên ở
     * tầng application, nơi claim được đúc.
     */
    private static final String CLAIM_SESSION_ID = "sid";

    /**
     * Thông điệp 429 của {@code forgot-password} — người dùng cuối đọc (§A.3).
     * <p>
     * <b>Cố ý không nêu ngưỡng và không nêu thời gian chờ chính xác</b>: con số đó chỉ giúp người
     * muốn lách nó canh cho vừa đủ.
     */
    private static final String MESSAGE_TOO_MANY_REQUESTS =
            "Bạn đã yêu cầu đặt lại mật khẩu quá nhiều lần, vui lòng thử lại sau ít phút.";

    private final AuthAppService authAppService;

    private final ForgotPasswordRateLimiter forgotPasswordRateLimiter;

    /**
     * @param request body đã qua validate
     * @return phiên vừa cấp
     */
    @Operation(summary = "Đăng ký tài khoản mới",
            description = """
                    Tạo tài khoản và **đăng nhập luôn** — trả về `AuthResponse` chứ không phải `201` \
                    rỗng, nên người dùng không phải nhập lại mật khẩu ngay sau khi đăng ký.

                    - Tài khoản mới nhận vai trò `CUSTOMER`; vai trò **không** xuất hiện trong \
                    response, nó nằm trong claim `roles` của access token.
                    - `user` trả về đúng 5 trường (`id`, `fullName`, `email`, `phone`, `avatar`) và \
                    **không bao giờ** chứa mật khẩu, kể cả dạng đã băm.
                    - Trường access token tên là **`token`**, không phải `accessToken`.
                    - `refreshToken` dùng cho `POST /api/auth/refresh`.""")
    @ApiResponse(responseCode = "200", description = "Đăng ký thành công, kèm phiên vừa cấp")
    @ApiResponse(responseCode = "409",
            description = "Email đã có tài khoản khác giữ; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Dữ liệu không hợp lệ. Kèm phần mở rộng **`errors`** — map `tên trường → thông \
                    điệp` (thông điệp validate hiện viết tiếng Anh); `detail` viết tiếng Việt.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("AuthController:->register | email={}", request.getEmail());
        return extractOrThrow(authAppService.register(AuthControllerMapper.toCommand(request)));
    }

    /**
     * @param request body đã qua validate
     * @return phiên vừa cấp
     */
    @Operation(summary = "Đăng nhập",
            description = """
                    Đổi email + mật khẩu lấy một phiên mới.

                    **Email không tồn tại và sai mật khẩu trả về cùng một `401` với cùng một chuỗi \
                    `detail`.** Đây là chủ ý, không phải thiếu sót: phân biệt hai ca sẽ biến endpoint \
                    này thành công cụ dò xem địa chỉ nào đã đăng ký.""")
    @ApiResponse(responseCode = "200", description = "Đăng nhập thành công, kèm phiên vừa cấp")
    @ApiResponse(responseCode = "401",
            description = "Sai email hoặc mật khẩu; `detail` viết tiếng Việt và giống nhau cho cả hai ca",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = "Thiếu email hoặc mật khẩu; kèm map `errors`",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        log.info("AuthController:->login | email={}", request.getEmail());
        return extractOrThrow(authAppService.login(AuthControllerMapper.toCommand(request)));
    }

    /**
     * @param request body đã qua validate
     * @return phiên mới; refresh token cũ bị thu hồi trong cùng transaction
     */
    @Operation(summary = "Gia hạn phiên",
            description = """
                    Đổi một refresh token còn hạn lấy **một cặp token mới**.

                    Cơ chế là **xoay vòng**: refresh token vừa dùng bị thu hồi ngay trong cùng một \
                    giao dịch với việc phát cặp mới, nên gọi lại đúng chuỗi cũ sẽ trả `401`. \
                    `client.ts` đã ghi đè cả hai token từ response nên phía frontend không phải sửa gì.

                    Ba trường hợp — token không tồn tại, đã bị thu hồi, đã hết hạn — trả **cùng một** \
                    `401`.""")
    @ApiResponse(responseCode = "200", description = "Cặp token mới")
    @ApiResponse(responseCode = "401",
            description = "Refresh token không tồn tại, đã bị thu hồi, hoặc đã hết hạn",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/auth/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("AuthController:->refresh");
        return extractOrThrow(authAppService.refresh(AuthControllerMapper.toCommand(request)));
    }

    /**
     * Thu hồi refresh token của phiên đang đăng xuất — trả 204 với thân rỗng.
     *
     * @param request body đã qua validate
     * @param jwt access token đã được filter chain giải mã; {@code sub} là id người dùng
     */
    @Operation(summary = "Đăng xuất",
            description = """
                    Thu hồi refresh token của phiên hiện tại (§B.4 #3) và trả **`204`** với **thân rỗng**.

                    Endpoint này **cần access token** — nó phải biết ai đang đăng xuất để chỉ thu hồi \
                    phiên của chính người đó; `userId` lấy từ claim `sub`, không nhận từ body.

                    Trả `204` kể cả khi chuỗi refresh token đã bị thu hồi từ trước hoặc không thuộc về \
                    người gọi: phân biệt các ca đó biến endpoint thành công cụ dò token.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "204", description = "Đã thu hồi; không có thân phản hồi",
            content = @Content)
    @ApiResponse(responseCode = "401",
            description = "Thiếu access token, token sai chữ ký hoặc đã hết hạn; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request,
                       @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        log.info("AuthController:->logout | userId={}", userId);
        authAppService.logout(AuthControllerMapper.toLogoutCommand(request, userId));
    }

    /**
     * Sửa hồ sơ của chính người đang đăng nhập — trả {@code UserResponse} đúng 5 trường.
     *
     * @param request body đã qua validate; trường vắng mặt hoặc {@code null} nghĩa là giữ nguyên
     * @param jwt access token đã được filter chain giải mã; {@code sub} là id người dùng
     * @return hồ sơ sau khi ghi
     */
    @Operation(summary = "Sửa hồ sơ người dùng",
            description = """
                    Cập nhật **từng phần** hồ sơ của chính người đang đăng nhập (§B.4).

                    - **Trường vắng mặt hoặc `null` nghĩa là GIỮ NGUYÊN giá trị cũ.** Không có cách \
                    nào xoá một trường qua endpoint này; chuỗi rỗng hoặc toàn khoảng trắng trả \
                    `422` kèm map `errors`.
                    - `id`, `role`, `avatar`, `passwordHash`, `createdAt` **không ghi đè được**, kể \
                    cả khi client gửi lên — chúng bị **bỏ qua trong im lặng**, không trả lỗi (§B.4 #2).
                    - `avatar` **chưa sửa được** ở đây: chưa có đường upload ảnh nào (backlog 0007).
                    - Người dùng được xác định bằng claim `sub` của access token, **không** nhận \
                    `userId` qua query / path / body.
                    - Response trả đúng 5 trường (`id`, `fullName`, `email`, `phone`, `avatar`) và \
                    **không bao giờ** chứa mật khẩu hay vai trò.
                    - **Đổi email KHÔNG thu hồi phiên nào**: token cũ vẫn dùng được tới `exp`.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Hồ sơ sau khi cập nhật")
    @ApiResponse(responseCode = "401",
            description = "Thiếu access token, token sai chữ ký hoặc đã hết hạn; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404",
            description = "Tài khoản ứng với token không còn tồn tại; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "Email mới đã có tài khoản khác giữ; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Dữ liệu không hợp lệ (chuỗi rỗng, email sai định dạng, vượt giới hạn độ dài). \
                    Kèm phần mở rộng **`errors`** — map `tên trường → thông điệp`.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping("/auth/me")
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        log.info("AuthController:->updateProfile | userId={}", userId);
        return extractOrThrow(authAppService.updateProfile(
                AuthControllerMapper.toUpdateProfileCommand(request, userId)));
    }

    /**
     * Đổi mật khẩu của chính người đang đăng nhập — trả 204 với thân rỗng.
     *
     * @param request body đã qua validate
     * @param jwt access token đã được filter chain giải mã; {@code sub} là id người dùng,
     *            {@code sid} là phiên hiện tại
     */
    @Operation(summary = "Đổi mật khẩu",
            description = """
                    Đổi mật khẩu của chính người đang đăng nhập và trả **`204`** với **thân rỗng**.

                    - **Sai `currentPassword` trả `422`, KHÔNG phải `401`.** 401 chỉ dành cho \
                    "thiếu / hỏng / hết hạn access token" — đó là ranh giới `client.ts` dựa vào để \
                    quyết định có tự gọi `/auth/refresh` hay không, nên một lần gõ nhầm mật khẩu cũ \
                    không được phép làm người dùng bị đăng xuất.
                    - Hai loại `422` phân biệt bằng khoá **`errors`**: lỗi validate **có** `errors`, \
                    sai mật khẩu cũ **không có**.
                    - **Thành công thì thu hồi mọi refresh token còn sống của tài khoản, TRỪ phiên \
                    đang gọi** — đổi mật khẩu đá được thiết bị khác ra mà không đá chính mình.
                    - **Access token của thiết bị bị đá vẫn dùng được tới `exp`** (tối đa 30 phút). \
                    Thu hồi một refresh token không huỷ được một JWT đã ký; bằng chứng của cú đá là \
                    `/auth/refresh` trả 401, không phải việc thiết bị đó lập tức mất quyền đọc.
                    - `newPassword` trùng `currentPassword` được cho phép.""",
            security = @SecurityRequirement(name = SECURITY_SCHEME))
    @ApiResponse(responseCode = "204", description = "Đã đổi mật khẩu; không có thân phản hồi",
            content = @Content)
    @ApiResponse(responseCode = "401",
            description = "Thiếu access token, token sai chữ ký hoặc đã hết hạn; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404",
            description = "Tài khoản ứng với token không còn tồn tại; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = """
                    Thiếu / sai định dạng trường (kèm map **`errors`**), **hoặc** sai \
                    `currentPassword` (**không** có `errors`); `detail` viết tiếng Việt.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping("/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        Long sessionId = extractSessionId(jwt);
        log.info("AuthController:->changePassword | userId={} sessionId={}", userId, sessionId);
        throwIfFailed(authAppService.changePassword(
                AuthControllerMapper.toChangePasswordCommand(request, userId, sessionId)));
    }

    /**
     * Nhận yêu cầu quên mật khẩu — trả 204 với thân rỗng, <b>luôn luôn</b>.
     *
     * @param request body đã qua validate
     * @param httpRequest chỉ dùng để lấy IP người gọi cho bộ chống dò tần suất
     */
    @Operation(summary = "Quên mật khẩu",
            description = """
                    Gửi một email chứa link đặt lại mật khẩu và trả **`204`** với **thân rỗng**.

                    - **Trả `204` kể cả khi email không ứng với tài khoản nào** (§B.4 điều 5). Đây là \
                    chủ ý, không phải thiếu sót: trả `404` sẽ biến endpoint này thành công cụ dò xem \
                    địa chỉ nào đã đăng ký. **Thời gian phản hồi cũng không tố cáo sự khác biệt** — \
                    email được gửi ở luồng khác nên cả hai nhánh trả về ở cùng một điểm.
                    - **`204` KHÔNG có nghĩa là email đã tới nơi.** Nó chỉ nói rằng yêu cầu đã được \
                    nhận. Kết quả gửi thật nằm ở log phía server.
                    - Link có hiệu lực **15 phút** (đổi được bằng `PASSWORD_RESET_TOKEN_TTL`) và chỉ \
                    dùng được **một lần**.
                    - Endpoint **công khai**, và **có giới hạn tần suất** theo cả IP lẫn địa chỉ \
                    email đích — vượt ngưỡng trả `429`.
                    - Link trỏ tới **trang frontend chưa tồn tại** ở thời điểm ticket này đóng; \
                    đường dẫn nằm sau biến môi trường `PASSWORD_RESET_URL`.""")
    @ApiResponse(responseCode = "204", description = "Đã nhận yêu cầu; không có thân phản hồi",
            content = @Content)
    @ApiResponse(responseCode = "422",
            description = """
                    Thiếu email hoặc email sai định dạng; kèm map **`errors`**. **Email đúng định \
                    dạng nhưng không có tài khoản nào KHÔNG rơi vào đây** — ca đó trả `204`.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429",
            description = "Yêu cầu quá nhiều lần trong một khoảng thời gian ngắn; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/auth/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                               HttpServletRequest httpRequest) {
        log.info("AuthController:->forgotPassword | email={}", request.getEmail());
        // Cong tan suat chay TRUOC khi cham vao app service: moi loi goi di qua duoc la mot email
        // that roi khoi he thong, nen day vua la chong do tai khoan vua la chong bien server thanh
        // may phat tan thu rac (backlog 0017 §Contract dieu 8).
        if (forgotPasswordRateLimiter.hasExceededLimit(httpRequest.getRemoteAddr(),
                request.getEmail())) {
            throw new TooManyRequestsException(MESSAGE_TOO_MANY_REQUESTS);
        }
        // Method nay chay @Async va tra ve NGAY. Do khong phai toi uu hieu nang ma la dieu kien de
        // hai nhanh "email co that" / "email khong ton tai" khong phan biet duoc bang THOI GIAN
        // phan hoi — mot con so da do duoc, xem javadoc AuthAppService.forgotPassword.
        authAppService.forgotPassword(AuthControllerMapper.toForgotPasswordCommand(request));
    }

    /**
     * Đặt lại mật khẩu bằng token nhận qua email — trả 204 với thân rỗng.
     *
     * @param request body đã qua validate
     */
    @Operation(summary = "Đặt lại mật khẩu bằng token",
            description = """
                    Đổi một token còn hạn lấy một mật khẩu mới, trả **`204`** với **thân rỗng**.

                    - **Endpoint này là suy diễn của backend, không đọc ra từ `API_CONTRACT`** — §B.4 \
                    chỉ khai `forgotPassword`. Tên endpoint và tên hai trường do backlog 0017 chọn; \
                    nếu tài liệu nguồn phía frontend chọn khác thì đây là một **thay đổi contract**.
                    - **Token dùng được đúng MỘT lần.** Gọi lại đúng chuỗi đó trả `422`.
                    - **Ba trường hợp — token không tồn tại, đã dùng, đã hết hạn — trả cùng một \
                    `422` với cùng một `detail`.** Phân biệt chúng sẽ nói cho người cầm một chuỗi \
                    bịa biết chuỗi đó có tồn tại hay không.
                    - **422, KHÔNG phải 401.** Người gọi đang chưa đăng nhập nên `401` vô nghĩa ở \
                    đây, và nó sẽ khiến `client.ts` gọi `/auth/refresh` với một phiên không tồn tại.
                    - Hai loại `422` phân biệt bằng khoá **`errors`**: lỗi validate **có** `errors`, \
                    token không dùng được **không có**.
                    - **Thành công thì thu hồi TOÀN BỘ refresh token của tài khoản**, không chừa \
                    phiên nào — khác `PUT /auth/password` (giữ phiên đang gọi), vì ở đây người dùng \
                    đang không đăng nhập và giả định phải là tài khoản đã bị chiếm.
                    - `newPassword` chịu cùng ràng buộc độ dài như `register` (6–72 ký tự).""")
    @ApiResponse(responseCode = "204", description = "Đã đặt lại mật khẩu; không có thân phản hồi",
            content = @Content)
    @ApiResponse(responseCode = "422",
            description = """
                    Thiếu / sai định dạng trường (kèm map **`errors`**), **hoặc** token không tồn \
                    tại / đã dùng / đã hết hạn (**không** có `errors`); `detail` viết tiếng Việt.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/auth/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        // KHONG log token va KHONG log mat khau — DB chi giu hash cua token, nen mot dong log se la
        // noi duy nhat chuoi tho con ton tai (§9).
        log.info("AuthController:->resetPassword");
        throwIfFailed(authAppService.resetPassword(
                AuthControllerMapper.toResetPasswordCommand(request)));
    }

    /**
     * Dịch kết quả đặt lại mật khẩu thành 204 hoặc exception.
     * <p>
     * Thành công đọc từ cờ {@code success}, và nhánh {@code default} nổ thành 500 — cùng lý do đã
     * viết ở {@link #throwIfFailed(PasswordMutationResponse)}.
     *
     * @param result kết quả của lệnh đặt lại mật khẩu
     */
    private void throwIfFailed(PasswordResetMutationResponse result) {
        if (result.isSuccess()) {
            return;
        }
        if (PasswordResetMutationResponse.CODE_INVALID_RESET_TOKEN.equals(result.getCode())) {
            throw new InvalidResetTokenException(result.getMessage());
        }
        throw new IllegalStateException("Unmapped password reset code " + result.getCode());
    }

    /**
     * Đọc claim {@code sid} — id dòng {@code refresh_token} của phiên đang gọi.
     * <p>
     * <b>Đọc bằng {@code getClaimAsString} rồi mới parse, không ép kiểu số.</b> Claim được đúc dưới
     * dạng chuỗi ở tầng application; ép kiểu một claim số đọc ngược ra là chỗ
     * {@code ClassCastException} nằm chờ, tuỳ cách số JSON được khử.
     * <p>
     * <b>Mọi ca không đọc được đều trả {@code null}, và {@code null} nghĩa là THU HỒI TẤT CẢ.</b>
     * Có hai ca như vậy và cả hai đều có thật: access token cấp <i>trước</i> backlog 0016 không
     * mang claim này, và một claim bị sửa tay thì không parse được thành số. Hướng hỏng bắt buộc là
     * <b>phía an toàn</b> — thà đá luôn phiên hiện tại còn hơn để một lần đổi mật khẩu không thu
     * hồi được phiên nào.
     *
     * @param jwt access token đã giải mã
     * @return id phiên, hoặc {@code null} khi claim vắng mặt / không phải số
     */
    private Long extractSessionId(Jwt jwt) {
        String sessionId = jwt.getClaimAsString(CLAIM_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("extractSessionId: token carries no sid, every session will be revoked");
            return null;
        }
        try {
            return Long.valueOf(sessionId);
        } catch (NumberFormatException e) {
            log.warn("extractSessionId: sid is not a number, every session will be revoked");
            return null;
        }
    }

    /**
     * Dịch kết quả sửa hồ sơ thành payload hoặc exception.
     * <p>
     * <b>Tập mã ở đây RỜI với tập của {@link #extractOrThrow(AuthMutationResponse)}</b>, và đó là
     * chủ ý: nhánh {@code default} của method kia ném {@code InvalidCredentialsException} → 401.
     * Gộp hai tập lại thì bất kỳ mã nào quên map sau này đều <i>âm thầm</i> thành 401 và
     * {@code client.ts} sẽ đăng xuất người dùng. Ở đây nhánh {@code default} nổ thành 500 — ồn ào,
     * tức là sửa được.
     *
     * @param result kết quả của lệnh sửa hồ sơ
     * @return hồ sơ khi thành công
     */
    private UserResponse extractOrThrow(ProfileMutationResponse result) {
        if (result.getUser() != null) {
            return result.getUser();
        }
        switch (result.getCode()) {
            case ProfileMutationResponse.CODE_USER_NOT_FOUND:
                throw new UserNotFoundException(result.getMessage());
            case ProfileMutationResponse.CODE_DUPLICATE_EMAIL:
                throw new DuplicateEmailException(result.getMessage());
            default:
                throw new IllegalStateException("Unmapped profile code " + result.getCode());
        }
    }

    /**
     * Dịch kết quả đổi mật khẩu thành 204 hoặc exception.
     * <p>
     * Thành công đọc từ cờ {@code success} chứ không từ {@code code == null}: endpoint này không có
     * payload nào để dùng làm dấu hiệu, và suy ra thành công từ sự vắng mặt của một mã sẽ biến một
     * lần quên set mã thành một lần đổi mật khẩu "thành công".
     * <p>
     * Nhánh {@code default} nổ thành 500 — cùng lý do đã viết ở
     * {@link #extractOrThrow(ProfileMutationResponse)}.
     *
     * @param result kết quả của lệnh đổi mật khẩu
     */
    private void throwIfFailed(PasswordMutationResponse result) {
        if (result.isSuccess()) {
            return;
        }
        switch (result.getCode()) {
            case PasswordMutationResponse.CODE_USER_NOT_FOUND:
                throw new UserNotFoundException(result.getMessage());
            case PasswordMutationResponse.CODE_INVALID_CURRENT_PASSWORD:
                throw new InvalidCurrentPasswordException(result.getMessage());
            default:
                throw new IllegalStateException("Unmapped password code " + result.getCode());
        }
    }

    /**
     * Dịch kết quả của tầng application thành payload hoặc exception.
     * <p>
     * Đây là chỗ duy nhất mã lỗi nghiệp vụ gặp mã HTTP: application không được biết HTTP, và kiểu
     * {@code *Exception} sống ở module controller (§3) nên application cũng không ném được chúng.
     *
     * @param result kết quả của lệnh xác thực
     * @return phiên khi thành công
     */
    private AuthResponse extractOrThrow(AuthMutationResponse result) {
        if (result.getAuth() != null) {
            return result.getAuth();
        }
        if (AuthMutationResponse.CODE_DUPLICATE_EMAIL.equals(result.getCode())) {
            throw new DuplicateEmailException(result.getMessage());
        }
        // CODE_INVALID_CREDENTIALS va CODE_INVALID_REFRESH_TOKEN cung ra 401: client.ts phan ung
        // voi 401 bang dung mot hanh vi, va hai ca deu la "phien nay khong dung duoc".
        throw new InvalidCredentialsException(result.getMessage());
    }
}
