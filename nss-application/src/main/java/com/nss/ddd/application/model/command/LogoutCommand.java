package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Lệnh đăng xuất — {@code POST /api/auth/logout}.
 * <p>
 * <b>{@code userId} đến từ JWT, không từ body</b> (§C.2). Nó có mặt trong command để việc thu hồi
 * bị giới hạn trong phạm vi người đang đăng nhập: không có nó, ai đoán được chuỗi refresh token
 * cũng đăng xuất được phiên của người khác.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class LogoutCommand {

    /** Lấy từ claim {@code sub} của access token. */
    private Long userId;

    private String refreshToken;
}
