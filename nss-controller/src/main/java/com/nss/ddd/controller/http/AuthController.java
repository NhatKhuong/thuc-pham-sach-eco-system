package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.AuthMutationResponse;
import com.nss.ddd.application.model.response.AuthResponse;
import com.nss.ddd.application.service.auth.AuthAppService;
import com.nss.ddd.controller.dto.LoginRequest;
import com.nss.ddd.controller.dto.RefreshTokenRequest;
import com.nss.ddd.controller.dto.RegisterRequest;
import com.nss.ddd.controller.exception.DuplicateEmailException;
import com.nss.ddd.controller.exception.InvalidCredentialsException;
import com.nss.ddd.controller.mapper.AuthControllerMapper;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
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
 * <b>Bốn endpoint của ticket này cố ý dừng ở đây.</b> {@code PUT /auth/me},
 * {@code PUT /auth/password} thuộc ticket hồ sơ người dùng; {@code POST /auth/forgot-password} bị
 * chặn bởi khoảng trống hạ tầng (không có chỗ lưu token đặt lại, không có đường gửi mail, và
 * frontend chưa có trang đặt lại mật khẩu) — xem mục Non-goals của backlog 0010.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Xác thực",
        description = "Đăng ký, đăng nhập, gia hạn phiên và đăng xuất.")
public class AuthController {

    /** Mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Tên security scheme khai ở {@code OpenApiConfig} — nút *Authorize* của Swagger UI. */
    private static final String SECURITY_SCHEME = "bearerAuth";

    private final AuthAppService authAppService;

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
