package com.nss.ddd.controller.mapper;

import com.nss.ddd.application.model.command.LoginCommand;
import com.nss.ddd.application.model.command.LogoutCommand;
import com.nss.ddd.application.model.command.RefreshCommand;
import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.controller.dto.LoginRequest;
import com.nss.ddd.controller.dto.RefreshTokenRequest;
import com.nss.ddd.controller.dto.RegisterRequest;

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
}
