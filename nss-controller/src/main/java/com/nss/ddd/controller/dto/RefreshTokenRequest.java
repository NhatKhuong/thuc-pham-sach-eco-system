package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code POST /api/auth/refresh} và {@code POST /api/auth/logout} — cả hai đều chỉ nhận
 * đúng một trường {@code refreshToken} (§B.4).
 * <p>
 * <b>Không có {@code userId} ở đây, và đó là chủ ý.</b> Với {@code refresh}, chủ sở hữu suy ra từ
 * chính dòng {@code refresh_token} trong DB; với {@code logout}, chủ sở hữu lấy từ JWT (§C.2).
 * Nhận {@code userId} từ body là mở đường cho việc thao tác trên phiên của người khác.
 * <p>
 * Trần 512 ký tự khớp cột {@code refresh_token.token}.
 */
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken must not be blank")
    @Size(max = 512, message = "refreshToken must not exceed 512 characters")
    private String refreshToken;
}
