package com.nss.ddd.controller.mapper;

import com.nss.ddd.application.model.command.ChangePasswordCommand;
import com.nss.ddd.application.model.command.ForgotPasswordCommand;
import com.nss.ddd.application.model.command.LoginCommand;
import com.nss.ddd.application.model.command.LogoutCommand;
import com.nss.ddd.application.model.command.RefreshCommand;
import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.command.ResendConfirmationCommand;
import com.nss.ddd.application.model.command.ResetPasswordCommand;
import com.nss.ddd.application.model.command.UpdateProfileCommand;
import com.nss.ddd.controller.dto.ChangePasswordRequest;
import com.nss.ddd.controller.dto.ForgotPasswordRequest;
import com.nss.ddd.controller.dto.LoginRequest;
import com.nss.ddd.controller.dto.RefreshTokenRequest;
import com.nss.ddd.controller.dto.RegisterRequest;
import com.nss.ddd.controller.dto.ResendConfirmationRequest;
import com.nss.ddd.controller.dto.ResetPasswordRequest;
import com.nss.ddd.controller.dto.UpdateProfileRequest;

/**
 * Converter ở ranh giới HTTP: {@code *Request} của controller sang {@code *Command} của application
 * (coding-conventions §7).
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard.
 * Không {@code BeanUtils.copyProperties}.
 * <p>
 * <b>{@link #toLogoutCommand} nhận {@code userId} như một tham số riêng</b>, không đọc nó từ
 * request: giá trị đó đến từ claim {@code sub} của access token chứ không từ body (§C.2). Chữ ký
 * method viết như vậy để việc "lấy userId từ body" không thể xảy ra do sơ ý.
 */
public final class AuthControllerMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private AuthControllerMapper() {
    }

    /**
     * @param request body của {@code POST /api/auth/register}
     * @return lệnh đăng ký, hoặc {@code null} khi {@code request} rỗng
     */
    public static RegisterCommand toCommand(RegisterRequest request) {
        if (request == null) {
            return null;
        }
        return new RegisterCommand()
                .setFullName(request.getFullName())
                .setEmail(request.getEmail())
                .setPhone(request.getPhone())
                .setPassword(request.getPassword());
    }

    /**
     * @param request body của {@code POST /api/auth/login}
     * @return lệnh đăng nhập, hoặc {@code null} khi {@code request} rỗng
     */
    public static LoginCommand toCommand(LoginRequest request) {
        if (request == null) {
            return null;
        }
        return new LoginCommand()
                .setEmail(request.getEmail())
                .setPassword(request.getPassword());
    }

    /**
     * @param request body của {@code POST /api/auth/refresh}
     * @return lệnh gia hạn, hoặc {@code null} khi {@code request} rỗng
     */
    public static RefreshCommand toCommand(RefreshTokenRequest request) {
        if (request == null) {
            return null;
        }
        return new RefreshCommand().setRefreshToken(request.getRefreshToken());
    }

    /**
     * @param request body của {@code POST /api/auth/logout}
     * @param userId chủ phiên, <b>lấy từ claim {@code sub} của access token</b>
     * @return lệnh đăng xuất, hoặc {@code null} khi {@code request} rỗng
     */
    public static LogoutCommand toLogoutCommand(RefreshTokenRequest request, Long userId) {
        if (request == null) {
            return null;
        }
        return new LogoutCommand()
                .setUserId(userId)
                .setRefreshToken(request.getRefreshToken());
    }

    /**
     * <b>{@code userId} là tham số riêng</b>, cùng lý do đã viết ở {@link #toLogoutCommand}: nó đến
     * từ claim {@code sub} chứ không từ body, và §C.4.1 gọi vi phạm ở đây là <i>rò rỉ dữ liệu</i>
     * chứ không phải lỗi hiển thị.
     * <p>
     * Ba trường hồ sơ được chép nguyên trạng, <b>kể cả khi là {@code null}</b> — {@code null} ở đây
     * mang nghĩa "giữ nguyên giá trị cũ" và việc diễn giải nó là của {@code UserMapper.applyPatch}.
     *
     * @param request body của {@code PUT /api/auth/me}
     * @param userId chủ hồ sơ, <b>lấy từ claim {@code sub} của access token</b>
     * @return lệnh sửa hồ sơ, hoặc {@code null} khi {@code request} rỗng
     */
    public static UpdateProfileCommand toUpdateProfileCommand(UpdateProfileRequest request,
                                                              Long userId) {
        if (request == null) {
            return null;
        }
        return new UpdateProfileCommand()
                .setUserId(userId)
                .setFullName(request.getFullName())
                .setEmail(request.getEmail())
                .setPhone(request.getPhone());
    }

    /**
     * <b>Cả {@code userId} lẫn {@code sessionId} đều là tham số riêng</b> — cả hai đọc từ access
     * token ({@code sub} và {@code sid}), không từ body. Chữ ký viết như vậy để việc nhận một trong
     * hai từ thứ client tự khai không thể xảy ra do sơ ý.
     *
     * @param request body của {@code PUT /api/auth/password}
     * @param userId chủ tài khoản, lấy từ claim {@code sub}
     * @param sessionId phiên đang gọi, lấy từ claim {@code sid}; <b>{@code null} là hợp lệ</b> —
     *                  token cấp trước ticket 0016 không mang claim này, và ca đó phải thu hồi mọi
     *                  phiên chứ không phải bỏ qua
     * @return lệnh đổi mật khẩu, hoặc {@code null} khi {@code request} rỗng
     */
    public static ChangePasswordCommand toChangePasswordCommand(ChangePasswordRequest request,
                                                                Long userId,
                                                                Long sessionId) {
        if (request == null) {
            return null;
        }
        return new ChangePasswordCommand()
                .setUserId(userId)
                .setSessionId(sessionId)
                .setCurrentPassword(request.getCurrentPassword())
                .setNewPassword(request.getNewPassword());
    }

    /**
     * <b>Không có tham số {@code userId}, và sự vắng mặt đó là chủ ý</b> — ngược hẳn với bốn method
     * trên. Endpoint {@code forgot-password} công khai: người gọi đang chưa đăng nhập nên không có
     * claim {@code sub} nào để đọc, và email trong body <i>không</i> định danh người gọi mà chỉ
     * chọn hộp thư nhận link.
     *
     * @param request body của {@code POST /api/auth/forgot-password}
     * @return lệnh quên mật khẩu, hoặc {@code null} khi {@code request} rỗng
     */
    public static ForgotPasswordCommand toForgotPasswordCommand(ForgotPasswordRequest request) {
        if (request == null) {
            return null;
        }
        return new ForgotPasswordCommand().setEmail(request.getEmail());
    }

    /**
     * <b>Không có tham số {@code userId}</b>, cùng lý do đã viết ở {@link #toForgotPasswordCommand}
     * — và ở đây hệ quả nặng hơn: chủ tài khoản được suy ra từ <i>dòng token</i>, không từ bất cứ
     * thứ gì client gửi lên. Thêm một tham số {@code userId} vào chữ ký này sẽ mở đường cho việc đổi
     * mật khẩu của người khác bằng một token hợp lệ của chính mình.
     *
     * @param request body của {@code POST /api/auth/reset-password}
     * @return lệnh đặt lại mật khẩu, hoặc {@code null} khi {@code request} rỗng
     */
    public static ResetPasswordCommand toResetPasswordCommand(ResetPasswordRequest request) {
        if (request == null) {
            return null;
        }
        return new ResetPasswordCommand()
                .setToken(request.getToken())
                .setNewPassword(request.getNewPassword());
    }

    /**
     * <b>Không có tham số {@code userId}</b>, cùng lý do đã viết ở {@link #toForgotPasswordCommand}
     * — endpoint công khai, email trong body chỉ chọn hộp thư nhận link.
     *
     * @param request body của {@code POST /api/auth/resend-confirmation}
     * @return lệnh gửi lại xác nhận, hoặc {@code null} khi {@code request} rỗng
     */
    public static ResendConfirmationCommand toResendConfirmationCommand(ResendConfirmationRequest request) {
        if (request == null) {
            return null;
        }
        return new ResendConfirmationCommand().setEmail(request.getEmail());
    }
}
